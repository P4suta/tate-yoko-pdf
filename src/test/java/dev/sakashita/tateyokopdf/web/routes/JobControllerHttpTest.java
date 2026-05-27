package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Error-path coverage for the JSON HTTP endpoints. */
final class JobControllerHttpTest {

  private static String firstHeader(Response resp, String name) {
    List<String> values = resp.headers().get(name);
    return (values == null || values.isEmpty()) ? "" : values.get(0);
  }

  @Test
  void postJobsWithoutFileReturns400AndJsonErrorBody() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.post("/api/jobs");
          assertThat(resp.code()).isEqualTo(400);
          assertThat(firstHeader(resp, "Content-Type")).startsWith("application/json");
          assertThat(resp.body().string()).contains("UPLOAD_EMPTY");
        });
  }

  @Test
  void getDownloadWithInvalidUuidReturnsJson404() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/api/jobs/not-a-uuid/download");
          assertThat(resp.code()).isEqualTo(404);
          assertThat(resp.body().string()).contains("JOB_NOT_FOUND");
        });
  }

  @Test
  void getDownloadWithUnknownUuidReturnsJson404() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/api/jobs/00000000-0000-0000-0000-000000000000/download");
          assertThat(resp.code()).isEqualTo(404);
          assertThat(resp.body().string()).contains("JOB_NOT_FOUND");
        });
  }

  @Test
  void unmatchedNonApiRouteFallsBackToSpaShell() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/some/spa/path");
          // Phase 1 stages frontend/build/index.html under classpath /static/ via
          // processResources; the SPA fallback in WebLauncher serves it as 200 HTML.
          assertThat(resp.code()).isEqualTo(200);
          assertThat(firstHeader(resp, "Content-Type")).startsWith("text/html");
        });
  }
}
