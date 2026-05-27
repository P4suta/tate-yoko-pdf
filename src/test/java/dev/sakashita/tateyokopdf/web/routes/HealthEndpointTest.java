package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

/** Smoke-test that the harness wires up all three health routes under the /api prefix. */
final class HealthEndpointTest {

  @Test
  void healthIsAJsonReadinessPayload() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/api/health");
          assertThat(resp.code()).isEqualTo(200);
          String body = resp.body().string();
          assertThat(body).contains("\"status\":\"UP\"");
          assertThat(body).contains("workDirWritable");
        });
  }

  @Test
  void livenessIsAvailable() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/api/health/live");
          assertThat(resp.code()).isEqualTo(200);
          assertThat(resp.body().string()).contains("\"status\":\"UP\"");
        });
  }

  @Test
  void readinessIsAvailable() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/api/health/ready");
          assertThat(resp.code()).isEqualTo(200);
        });
  }
}
