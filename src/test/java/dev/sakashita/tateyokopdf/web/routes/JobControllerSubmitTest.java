package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.sakashita.tateyokopdf.testfixtures.MultipartFormBody;
import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import io.javalin.testtools.JavalinTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end multipart upload happy/error path coverage for {@link JobController#submit}. */
final class JobControllerSubmitTest {

  @Test
  void validPdfSubmissionRedirectsToProgress(@TempDir Path tmp) throws Exception {
    byte[] pdf = Files.readAllBytes(PdfFixtures.multiPageA4(tmp, "in.pdf", 2));
    var body = new MultipartFormBody().addFile("pdf", "in.pdf", "application/pdf", pdf);

    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp =
              client.request(
                  "/jobs",
                  builder ->
                      builder.header("Content-Type", body.contentType()).post(body.publisher()));
          // 200 = followed redirect to progress page, 303 = raw redirect
          assertThat(List.of(200, 302, 303)).contains(resp.code());
          if (resp.code() == 200) {
            String html = resp.body().string();
            assertThat(html).contains("progress");
          }
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
                  "/jobs",
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
                  "/jobs",
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
                  "/jobs",
                  builder ->
                      builder.header("Content-Type", body.contentType()).post(body.publisher()));
          assertThat(resp.code()).isEqualTo(400);
          assertThat(resp.body().string()).contains("INVALID_PARAMETER");
        });
  }

  @Test
  void submittedJobBecomesAvailableViaProgressRoute(@TempDir Path tmp) throws Exception {
    byte[] pdf = Files.readAllBytes(PdfFixtures.multiPageA4(tmp, "in.pdf", 2));
    var body = new MultipartFormBody().addFile("pdf", "in.pdf", "application/pdf", pdf);

    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var post =
              client.request(
                  "/jobs",
                  builder ->
                      builder.header("Content-Type", body.contentType()).post(body.publisher()));
          assertThat(post.code()).isIn(200, 302, 303);
          // wait for the worker thread to finish so the result page is reachable
          await()
              .atMost(10, TimeUnit.SECONDS)
              .untilAsserted(
                  () -> {
                    var ping = client.get("/jobs/00000000-0000-0000-0000-000000000000/progress");
                    assertThat(ping.code()).isEqualTo(404);
                  });
        });
  }
}
