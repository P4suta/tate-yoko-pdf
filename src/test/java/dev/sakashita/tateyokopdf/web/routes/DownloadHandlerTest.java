package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the HTTP download contract: full-body 200 with {@code Content-Length} and {@code
 * Accept-Ranges}, single-range 206 with {@code Content-Range}, unsatisfiable 416, and survival
 * across two range requests against the same job (a regression guard against the prior eager-
 * cleanup hook that deleted the output the moment the first response stream closed).
 */
final class DownloadHandlerTest {

  /**
   * Synthesise something PDF-shaped large enough that Range slices are clearly observable. A few
   * hundred KB of repeating bytes between the {@code %PDF-} prologue and {@code %%EOF} epilogue is
   * plenty. All bytes are ASCII so we can read body slices back as Strings without charset
   * surprises.
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

  private static Job registerJob(JobRegistry registry, Path tmp, byte[] body, String name)
      throws Exception {
    Path workDir = Files.createDirectories(tmp.resolve("work-" + name));
    Path output = workDir.resolve(name + "_spread.pdf");
    Files.write(output, body);
    return registry.register(workDir, workDir.resolve("input.pdf"), output, name + ".pdf", "trace");
  }

  private static HttpResponse<byte[]> get(String origin) throws Exception {
    return send(HttpRequest.newBuilder().GET().uri(URI.create(origin + "/download")).build());
  }

  private static HttpResponse<byte[]> getRange(String origin, String range) throws Exception {
    return send(
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(origin + "/download"))
            .header("Range", range)
            .build());
  }

  private static HttpResponse<byte[]> send(HttpRequest request) throws Exception {
    try (HttpClient http = HttpClient.newHttpClient()) {
      return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
  }

  @Test
  void fullBodyReturns200WithContentLengthAndAcceptRanges(@TempDir Path tmp) throws Exception {
    byte[] expected = fakePdf();
    Job job = registerJob(new JobRegistry(), tmp, expected, "full");

    JavalinTest.test(
        appFor(new DownloadHandler(), job),
        (server, client) -> {
          HttpResponse<byte[]> resp = get(client.getOrigin());
          assertThat(resp.statusCode()).isEqualTo(200);
          assertThat(resp.headers().firstValue("Accept-Ranges")).hasValue("bytes");
          assertThat(resp.headers().firstValue("Content-Length"))
              .hasValue(Integer.toString(expected.length));
          assertThat(resp.body()).hasSize(expected.length).isEqualTo(expected);
        });
  }

  @Test
  void closedRangeReturns206WithCorrectSlice(@TempDir Path tmp) throws Exception {
    byte[] expected = fakePdf();
    Job job = registerJob(new JobRegistry(), tmp, expected, "closed");

    JavalinTest.test(
        appFor(new DownloadHandler(), job),
        (server, client) -> {
          HttpResponse<byte[]> resp = getRange(client.getOrigin(), "bytes=0-1023");
          assertThat(resp.statusCode()).isEqualTo(206);
          assertThat(resp.headers().firstValue("Content-Range"))
              .hasValue("bytes 0-1023/" + expected.length);
          assertThat(resp.headers().firstValue("Content-Length")).hasValue("1024");
          assertThat(resp.body()).hasSize(1024).isEqualTo(Arrays.copyOfRange(expected, 0, 1024));
        });
  }

  @Test
  void openEndedRangeReturns206ToEnd(@TempDir Path tmp) throws Exception {
    byte[] expected = fakePdf();
    Job job = registerJob(new JobRegistry(), tmp, expected, "open");

    JavalinTest.test(
        appFor(new DownloadHandler(), job),
        (server, client) -> {
          HttpResponse<byte[]> resp = getRange(client.getOrigin(), "bytes=100-");
          assertThat(resp.statusCode()).isEqualTo(206);
          long lastByte = expected.length - 1L;
          assertThat(resp.headers().firstValue("Content-Range"))
              .hasValue("bytes 100-" + lastByte + "/" + expected.length);
          assertThat(resp.headers().firstValue("Content-Length"))
              .hasValue(Long.toString(expected.length - 100L));
          assertThat(resp.body()).isEqualTo(Arrays.copyOfRange(expected, 100, expected.length));
        });
  }

  @Test
  void outOfRangeReturns416WithStarContentRange(@TempDir Path tmp) throws Exception {
    byte[] expected = fakePdf();
    Job job = registerJob(new JobRegistry(), tmp, expected, "oob");

    JavalinTest.test(
        appFor(new DownloadHandler(), job),
        (server, client) -> {
          long beyond = expected.length + 10L;
          HttpResponse<byte[]> resp =
              getRange(client.getOrigin(), "bytes=" + expected.length + "-" + beyond);
          assertThat(resp.statusCode()).isEqualTo(416);
          assertThat(resp.headers().firstValue("Content-Range"))
              .hasValue("bytes */" + expected.length);
        });
  }

  @Test
  void backToBackRangeRequestsBothSucceed(@TempDir Path tmp) throws Exception {
    // Regression guard: the old FilterInputStream-based cleanup deleted the file on first close,
    // which would 404 the second range request. The TTL GC takes over now and the file must
    // survive multiple GETs.
    byte[] expected = fakePdf();
    Job job = registerJob(new JobRegistry(), tmp, expected, "twice");

    JavalinTest.test(
        appFor(new DownloadHandler(), job),
        (server, client) -> {
          HttpResponse<byte[]> first = getRange(client.getOrigin(), "bytes=0-99");
          assertThat(first.statusCode()).isEqualTo(206);

          HttpResponse<byte[]> second = getRange(client.getOrigin(), "bytes=100-199");
          assertThat(second.statusCode()).isEqualTo(206);
          assertThat(second.headers().firstValue("Content-Range"))
              .hasValue("bytes 100-199/" + expected.length);
          assertThat(second.body()).isEqualTo(Arrays.copyOfRange(expected, 100, 200));
        });
  }
}
