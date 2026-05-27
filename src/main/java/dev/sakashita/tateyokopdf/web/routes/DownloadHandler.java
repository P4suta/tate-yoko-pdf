package dev.sakashita.tateyokopdf.web.routes;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.web.job.Job;
import io.javalin.http.Context;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.jspecify.annotations.Nullable;

/**
 * Streams a finished job's output PDF to the client with HTTP Range support so the browser's
 * built-in PDF viewer can render the linearized PDF's first page before the whole body arrives.
 *
 * <p>Cleanup of the on-disk output is deliberately not anchored on this download — Range requests
 * issue multiple HTTP GETs against the same file, and eager-deleting on the first close-of-stream
 * would 404 the follow-ups. Lifecycle is delegated to {@code TempFileGc}, which sweeps expired jobs
 * out of the job registry on a TTL.
 */
public final class DownloadHandler {

  private static final int COPY_BUFFER_BYTES = 64 * 1024;

  public void serve(Context ctx, Job job) {
    Path output = job.outputPath();
    if (!Files.isRegularFile(output)) {
      throw SpreadException.of(ErrorKind.JOB_OUTPUT_GONE);
    }
    long totalBytes;
    try {
      totalBytes = Files.size(output);
    } catch (IOException e) {
      throw SpreadException.withDetail(ErrorKind.INTERNAL, "stat output failed", e);
    }

    // Bypass Javalin's compression layer so Content-Length / Content-Range match wire bytes
    // verbatim. application/pdf is opaque and rarely benefits from gzip anyway, and Jetty
    // throws `IOException: written < content-length` when explicit Content-Length disagrees
    // with the post-compression byte count.
    ctx.disableCompression();
    ctx.contentType("application/pdf");
    ctx.header("Accept-Ranges", "bytes");
    ctx.header(
        "Content-Disposition",
        "inline; filename*=UTF-8''" + urlEncode(downloadName(job.originalName())));

    ByteRange range = ByteRange.parse(ctx.header("Range"), totalBytes);
    if (range == null) {
      ctx.status(200);
      ctx.header("Content-Length", Long.toString(totalBytes));
      writeSlice(ctx, output, 0L, totalBytes);
      return;
    }
    if (!range.satisfiable) {
      ctx.status(416);
      ctx.header("Content-Range", "bytes */" + totalBytes);
      ctx.header("Content-Length", "0");
      return;
    }
    long length = range.end - range.start + 1;
    ctx.status(206);
    ctx.header("Content-Range", "bytes " + range.start + "-" + range.end + "/" + totalBytes);
    ctx.header("Content-Length", Long.toString(length));
    writeSlice(ctx, output, range.start, length);
  }

  private static void writeSlice(Context ctx, Path output, long start, long length) {
    OutputStream os = ctx.outputStream();
    try (FileChannel src = FileChannel.open(output, StandardOpenOption.READ)) {
      src.position(start);
      ByteBuffer buf = ByteBuffer.allocate(COPY_BUFFER_BYTES);
      long remaining = length;
      while (remaining > 0L) {
        buf.clear();
        if (buf.capacity() > remaining) {
          buf.limit((int) remaining);
        }
        int read = src.read(buf);
        if (read <= 0) {
          break;
        }
        os.write(buf.array(), 0, read);
        remaining -= read;
      }
      os.flush();
    } catch (IOException e) {
      throw SpreadException.withDetail(ErrorKind.INTERNAL, "download write failed", e);
    }
  }

  private static String downloadName(String originalName) {
    String base = originalName.replaceFirst("(?i)\\.pdf$", "");
    return base + "_spread.pdf";
  }

  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * Single-range subset of <a href="https://www.rfc-editor.org/rfc/rfc9110#field.range">RFC 9110
   * §14</a>. Multipart byteranges are intentionally unsupported: every mainstream browser PDF
   * viewer issues single-range requests against linearized PDFs.
   */
  static final class ByteRange {
    final long start;
    final long end; // inclusive
    final boolean satisfiable;

    private ByteRange(long start, long end, boolean satisfiable) {
      this.start = start;
      this.end = end;
      this.satisfiable = satisfiable;
    }

    /**
     * Returns {@code null} when the header is absent, malformed, or specifies multiple ranges
     * (callers fall back to a 200 full-body response). Returns a range with {@code
     * satisfiable=false} for an in-spec but out-of-bounds request (callers respond 416).
     */
    static @Nullable ByteRange parse(@Nullable String header, long totalBytes) {
      if (header == null || header.isBlank() || !header.startsWith("bytes=")) {
        return null;
      }
      String spec = header.substring("bytes=".length()).trim();
      if (spec.contains(",")) {
        return null;
      }
      int dash = spec.indexOf('-');
      if (dash < 0) {
        return null;
      }
      String first = spec.substring(0, dash).trim();
      String second = spec.substring(dash + 1).trim();
      try {
        if (first.isEmpty()) {
          // Suffix range: bytes=-N → last N bytes.
          if (second.isEmpty()) {
            return null;
          }
          long suffix = Long.parseLong(second);
          if (suffix <= 0L || totalBytes == 0L) {
            return new ByteRange(0L, 0L, false);
          }
          long startPos = Math.max(0L, totalBytes - suffix);
          return new ByteRange(startPos, totalBytes - 1L, true);
        }
        long start = Long.parseLong(first);
        long end = second.isEmpty() ? totalBytes - 1L : Long.parseLong(second);
        if (start < 0L || end < start) {
          return null;
        }
        if (start >= totalBytes) {
          return new ByteRange(start, end, false);
        }
        long clampedEnd = Math.min(end, totalBytes - 1L);
        return new ByteRange(start, clampedEnd, true);
      } catch (NumberFormatException nfe) {
        return null;
      }
    }
  }
}
