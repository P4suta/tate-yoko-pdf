package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.MultipartFormBody;
import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end multipart upload happy/error path coverage for {@link JobController#submit}. */
final class JobControllerSubmitTest {

  private static String firstHeader(Response resp, String name) {
    List<String> values = resp.headers().get(name);
    return (values == null || values.isEmpty()) ? "" : values.get(0);
  }

  @Test
  void validPdfSubmissionReturnsAcceptedWithJsonJobId(@TempDir Path tmp) throws Exception {
    byte[] pdf = Files.readAllBytes(PdfFixtures.multiPageA4(tmp, "in.pdf", 2));
    var body = new MultipartFormBody().addFile("pdf", "in.pdf", "application/pdf", pdf);

    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp =
              client.request(
                  "/api/jobs",
                  builder ->
                      builder.header("Content-Type", body.contentType()).post(body.publisher()));
          assertThat(resp.code()).isEqualTo(202);
          assertThat(firstHeader(resp, "Content-Type")).startsWith("application/json");
          String json = resp.body().string();
          assertThat(json).startsWith("{\"id\":\"");
          // Verify the id is a parseable UUID
          String id = json.substring("{\"id\":\"".length(), json.length() - 2);
          assertThat(UUID.fromString(id)).isNotNull();
        });
  }

  @Test
  void txtRenamedAsPdfRejectedByMagicCheck(@TempDir Path tmp) throws Exception {
    byte[] notPdf = "this is plain text not pdf".getBytes(StandardCharsets.UTF_8);
    var body = new MultipartFormBody().addFile("pdf", "fake.pdf", "application/pdf", notPdf);

    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp =
              client.request(
                  "/api/jobs",
                  builder ->
                      builder.header("Content-Type", body.contentType()).post(body.publisher()));
          assertThat(resp.code()).isEqualTo(400);
          assertThat(resp.body().string()).contains("UPLOAD_INVALID");
        });
  }

  @Test
  void wrongExtensionRejected(@TempDir Path tmp) throws Exception {
    byte[] pdf = Files.readAllBytes(PdfFixtures.multiPageA4(tmp, "in.pdf", 1));
    var body = new MultipartFormBody().addFile("pdf", "notes.txt", "application/pdf", pdf);
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp =
              client.request(
                  "/api/jobs",
                  builder ->
                      builder.header("Content-Type", body.contentType()).post(body.publisher()));
          assertThat(resp.code()).isEqualTo(400);
          assertThat(resp.body().string()).contains("UPLOAD_INVALID");
        });
  }

  @Test
  void invalidDirectionRejected(@TempDir Path tmp) throws Exception {
    byte[] pdf = Files.readAllBytes(PdfFixtures.multiPageA4(tmp, "in.pdf", 2));
    var body =
        new MultipartFormBody()
            .addField("direction", "SIDEWAYS")
            .addFile("pdf", "in.pdf", "application/pdf", pdf);
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp =
              client.request(
                  "/api/jobs",
                  builder ->
                      builder.header("Content-Type", body.contentType()).post(body.publisher()));
          assertThat(resp.code()).isEqualTo(400);
          assertThat(resp.body().string()).contains("INVALID_PARAMETER");
        });
  }
}
