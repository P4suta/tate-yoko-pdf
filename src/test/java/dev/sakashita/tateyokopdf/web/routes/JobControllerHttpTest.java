package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

/**
 * Error-path coverage for HTTP endpoints. The happy-path multipart upload flow is exercised in M3
 * once {@code UploadValidator} is in place and a multipart helper exists.
 */
final class JobControllerHttpTest {

  @Test
  void postJobsWithoutFileReturns400AndErrorPage() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.post("/jobs");
          assertThat(resp.code()).isEqualTo(400);
          assertThat(resp.body().string()).contains("UPLOAD_EMPTY");
        });
  }

  @Test
  void getProgressWithInvalidUuidReturns404() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/jobs/not-a-uuid/progress");
          assertThat(resp.code()).isEqualTo(404);
          assertThat(resp.body().string()).contains("JOB_NOT_FOUND");
        });
  }

  @Test
  void getProgressWithUnknownJobIdReturns404() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/jobs/00000000-0000-0000-0000-000000000000/progress");
          assertThat(resp.code()).isEqualTo(404);
          assertThat(resp.body().string()).contains("JOB_NOT_FOUND");
        });
  }

  @Test
  void getResultWithInvalidUuidReturns404() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/jobs/not-a-uuid/result");
          assertThat(resp.code()).isEqualTo(404);
        });
  }

  @Test
  void getDownloadWithInvalidUuidReturns404() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/jobs/not-a-uuid/download");
          assertThat(resp.code()).isEqualTo(404);
        });
  }
}
