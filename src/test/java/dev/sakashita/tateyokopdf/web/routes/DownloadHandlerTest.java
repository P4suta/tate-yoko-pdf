package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the HTTP-1.1 download contract under compression. A previous version of {@link
 * DownloadHandler} set {@code Content-Length} to the on-disk PDF size, which Jetty later found
 * inconsistent with the post-gzip byte count and rejected with {@code IOException: written <
 * content-length}. The compressed-path test below would have caught that regression — {@code
 * JobControllerHttpTest} only exercises the 404 paths.
 */
final class DownloadHandlerTest {

  /**
   * Synthesise something PDF-shaped large enough to make gzip's buffer flush observable in the
   * response. A few hundred KB of repeating bytes between the {@code %PDF-} prologue and {@code
   * %%EOF} epilogue is enough. All bytes are ASCII so we can read the body back as a String without
   * charset surprises.
   */
  private static byte[] fakePdf() {
    byte[] header = "%PDF-1.5\n".getBytes(StandardCharsets.US_ASCII);
    byte[] footer = "%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    byte[] payload = new byte[200_000];
    Arrays.fill(payload, (byte) 'A');
    byte[] all = new byte[header.length + payload.length + footer.length];
    System.arraycopy(header, 0, all, 0, header.length);
    System.arraycopy(payload, 0, all, header.length, payload.length);
    System.arraycopy(footer, 0, all, header.length + payload.length, footer.length);
    return all;
  }

  private static Javalin appFor(DownloadHandler handler, Job job) {
    return Javalin.create(config -> config.routes.get("/download", ctx -> handler.serve(ctx, job)));
  }

  @Test
  void compressedDownloadStreamsFullBody(@TempDir Path tmp) throws Exception {
    JobRegistry registry = new JobRegistry();
    Path workDir = Files.createDirectories(tmp.resolve("work"));
    Path output = workDir.resolve("foo_spread.pdf");
    byte[] expected = fakePdf();
    Files.write(output, expected);

    Job job =
        registry.register(workDir, workDir.resolve("input.pdf"), output, "foo.pdf", "trace-test");

    JavalinTest.test(
        appFor(new DownloadHandler(registry), job),
        (server, client) -> {
          // OkHttp defaults to `Accept-Encoding: gzip` and transparently decodes the response.
          // (Setting the header explicitly opts the client out of transparent decoding, which is
          // not what we want — the bug fix is about the server completing the gzipped response
          // without a Content-Length mismatch; transparent decoding on the client side is fine.)
          var resp = client.get("/download");

          assertThat(resp.code()).isEqualTo(200);

          // If Jetty had thrown on close (the pre-fix behaviour) the body would come back short
          // or `string()` would surface the IOException — both fail this check.
          String body = resp.body().string();
          assertThat(body).hasSize(expected.length);
          assertThat(body).startsWith("%PDF-1.5").endsWith("%%EOF\n");
        });
  }

  @Test
  void plainDownloadStreamsFullBody(@TempDir Path tmp) throws Exception {
    // Belt-and-braces: also verify the non-compressed path. Removing Content-Length should not
    // break clients that do not advertise Accept-Encoding either.
    JobRegistry registry = new JobRegistry();
    Path workDir = Files.createDirectories(tmp.resolve("work"));
    Path output = workDir.resolve("bar_spread.pdf");
    byte[] expected = fakePdf();
    Files.write(output, expected);

    Job job =
        registry.register(workDir, workDir.resolve("input.pdf"), output, "bar.pdf", "trace-test");

    JavalinTest.test(
        appFor(new DownloadHandler(registry), job),
        (server, client) -> {
          var resp = client.get("/download");
          assertThat(resp.code()).isEqualTo(200);
          String body = resp.body().string();
          assertThat(body).hasSize(expected.length);
          assertThat(body).startsWith("%PDF-1.5").endsWith("%%EOF\n");
        });
  }
}
