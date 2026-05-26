package dev.sakashita.tateyokopdf.web.upload;

import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Defence-in-depth checks on user-uploaded PDFs: size, filename hygiene, Content-Type, and a {@code
 * %PDF} magic-byte probe so a renamed text file cannot reach PDFBox.
 */
public final class UploadValidator {

  private static final int MAX_FILENAME_BYTES = 255;
  private static final Set<String> ACCEPTABLE_CONTENT_TYPES =
      Set.of("application/pdf", "application/octet-stream", "application/x-pdf");

  /** Validates the upload and returns a path-safe, NFC-normalised filename. */
  public String validate(@Nullable UploadedFile file) {
    if (file == null || file.size() == 0) {
      throw SpreadException.of(ErrorKind.UPLOAD_EMPTY);
    }

    String rawFilename = Objects.requireNonNullElse(file.filename(), "");
    String basename = basename(rawFilename);
    if (basename.isBlank()) {
      throw SpreadException.withDetail(ErrorKind.UPLOAD_INVALID, "blank filename", null);
    }

    String normalised = Normalizer.normalize(basename, Normalizer.Form.NFC);
    if (normalised.getBytes(StandardCharsets.UTF_8).length > MAX_FILENAME_BYTES) {
      throw SpreadException.withDetail(
          ErrorKind.UPLOAD_INVALID,
          "filename exceeds " + MAX_FILENAME_BYTES + " bytes UTF-8",
          null);
    }

    if (!endsWithPdfIgnoreCase(normalised)) {
      throw SpreadException.withDetail(
          ErrorKind.UPLOAD_INVALID, "extension is not .pdf: " + normalised, null);
    }

    String contentType = file.contentType();
    if (contentType != null) {
      String head = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
      if (!ACCEPTABLE_CONTENT_TYPES.contains(head)) {
        throw SpreadException.withDetail(
            ErrorKind.UPLOAD_INVALID, "content-type=" + contentType, null);
      }
    }

    try (InputStream in = file.content()) {
      byte[] header = in.readNBytes(4);
      if (header.length != 4
          || header[0] != '%'
          || header[1] != 'P'
          || header[2] != 'D'
          || header[3] != 'F') {
        throw SpreadException.withDetail(
            ErrorKind.UPLOAD_INVALID, "magic header missing %PDF", null);
      }
    } catch (IOException e) {
      throw SpreadException.withDetail(ErrorKind.INTERNAL, "magic-byte probe failed", e);
    }

    return normalised;
  }

  /** Strip any directory parts; defends against {@code ../} or absolute paths from clients. */
  private static String basename(String filename) {
    String stripped = filename.replace('\\', '/');
    int slash = stripped.lastIndexOf('/');
    return slash >= 0 ? stripped.substring(slash + 1) : stripped;
  }

  private static boolean endsWithPdfIgnoreCase(String name) {
    return name.length() >= 4 && name.regionMatches(true, name.length() - 4, ".pdf", 0, 4);
  }
}
