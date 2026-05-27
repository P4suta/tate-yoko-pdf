package dev.sakashita.tateyokopdf.web.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import io.javalin.http.UploadedFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.jspecify.annotations.Nullable;

/**
 * Property-based fuzz over {@link UploadValidator#validate}. The contract is binary: the validator
 * either returns a normalised filename string (success) or throws a {@link SpreadException}
 * (well-formed rejection). It must never NPE, never return null, never leak an arbitrary
 * RuntimeException, no matter what bytes the client puts in the filename or Content-Type header.
 */
final class UploadValidatorProperties {

  @Property
  void validatorEitherReturnsAStringOrThrowsSpreadException(
      @ForAll @From("filenames") String filename,
      @ForAll @From("contentTypes") @Nullable String contentType,
      @ForAll("payloads") byte[] payload) {
    UploadValidator validator = new UploadValidator();
    UploadedFile file = fakeUpload(filename, contentType, payload);
    try {
      String result = validator.validate(file);
      // Success: the returned filename must end in .pdf (case-insensitive) and be
      // non-blank — the validator's documented post-condition.
      assertThat(result).isNotBlank();
      assertThat(result.toLowerCase(Locale.ROOT)).endsWith(".pdf");
    } catch (SpreadException ok) {
      // Rejection branch: any SpreadException is an acceptable outcome.
    }
    // Any other Throwable would propagate and fail the property — which is what we want.
  }

  @Provide
  Arbitrary<String> filenames() {
    // Mix sane names, path-traversal attempts, Unicode, mixed case, and empty-ish strings.
    Arbitrary<String> safe =
        Arbitraries.strings().alpha().numeric().ofMaxLength(40).map(s -> s + ".pdf");
    Arbitrary<String> traversal =
        Arbitraries.of("../foo.pdf", "../../etc/passwd", "/abs/path.pdf", "foo\\bar.pdf");
    Arbitrary<String> wrongExt =
        Arbitraries.strings().alpha().numeric().ofMaxLength(20).map(s -> s + ".txt");
    Arbitrary<String> emptyish = Arbitraries.of("", " ", ".pdf", "/");
    Arbitrary<String> overlong = Arbitraries.strings().alpha().ofLength(300).map(s -> s + ".pdf");
    Arbitrary<String> unicode = Arbitraries.strings().ofMaxLength(50).map(s -> s + ".pdf");
    return Arbitraries.frequencyOf(
        net.jqwik.api.Tuple.of(40, safe),
        net.jqwik.api.Tuple.of(10, traversal),
        net.jqwik.api.Tuple.of(10, wrongExt),
        net.jqwik.api.Tuple.of(10, emptyish),
        net.jqwik.api.Tuple.of(10, overlong),
        net.jqwik.api.Tuple.of(20, unicode));
  }

  @Provide
  Arbitrary<@Nullable String> contentTypes() {
    return Arbitraries.of(
        "application/pdf",
        "application/octet-stream",
        "application/x-pdf",
        "text/plain",
        "application/pdf; charset=binary",
        "",
        null);
  }

  @Provide
  Arbitrary<byte[]> payloads() {
    Arbitrary<byte[]> validPdfHeader =
        Arbitraries.of("%PDF-1.5\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
    Arbitrary<byte[]> truncated = Arbitraries.of("%PD".getBytes(StandardCharsets.US_ASCII));
    Arbitrary<byte[]> wrongMagic =
        Arbitraries.of("plain text data".getBytes(StandardCharsets.US_ASCII));
    Arbitrary<byte[]> empty = Arbitraries.of(new byte[0]);
    return Arbitraries.frequencyOf(
        net.jqwik.api.Tuple.of(50, validPdfHeader),
        net.jqwik.api.Tuple.of(20, truncated),
        net.jqwik.api.Tuple.of(20, wrongMagic),
        net.jqwik.api.Tuple.of(10, empty));
  }

  /**
   * Build an {@link UploadedFile} stand-in via Mockito because the Javalin type is a Kotlin class
   * (not an interface) and instantiating it directly would require pulling in jakarta multipart
   * lifecycle wiring. Stubs are {@code lenient()} since the validator's control flow only reaches a
   * subset of getters on any given input.
   */
  private static UploadedFile fakeUpload(
      String filename, @Nullable String contentType, byte[] payload) {
    UploadedFile file = mock(UploadedFile.class);
    lenient().when(file.filename()).thenReturn(filename);
    lenient().when(file.contentType()).thenReturn(contentType == null ? "" : contentType);
    lenient().when(file.size()).thenReturn((long) payload.length);
    lenient().when(file.content()).thenReturn(new ByteArrayInputStream(payload));
    return file;
  }
}
