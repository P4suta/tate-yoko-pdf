package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

final class HealthEndpointTest {

  @Test
  void healthReturnsOk() {
    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var resp = client.get("/health");
          assertThat(resp.code()).isEqualTo(200);
          assertThat(resp.body().string()).isEqualTo("OK");
        });
  }
}
