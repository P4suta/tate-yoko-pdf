package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock(value = "observability.ShutdownState", mode = ResourceAccessMode.READ_WRITE)
final class HealthControllerTest {

  @AfterEach
  void resetShutdownState() {
    ShutdownState.reset();
  }

  private static Javalin app(HealthController health) {
    return Javalin.create(
        config -> {
          config.routes.get("/health", health::health);
          config.routes.get("/health/live", health::liveness);
          config.routes.get("/health/ready", health::readiness);
        });
  }

  private static HealthController upController(Path workDir) {
    var workers =
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    return new HealthController(new HealthCheck(new JobRegistry(), workers, workDir, 1L));
  }

  @Test
  void livenessReturns200UpByDefault(@TempDir Path tmp) {
    JavalinTest.test(
        app(upController(tmp)),
        (server, client) -> {
          var resp = client.get("/health/live");
          assertThat(resp.code()).isEqualTo(200);
          assertThat(resp.body().string()).contains("\"status\":\"UP\"");
        });
  }

  @Test
  void readinessReturns200WithChecks(@TempDir Path tmp) {
    JavalinTest.test(
        app(upController(tmp)),
        (server, client) -> {
          var resp = client.get("/health/ready");
          assertThat(resp.code()).isEqualTo(200);
          String body = resp.body().string();
          assertThat(body).contains("\"status\":\"UP\"");
          assertThat(body).contains("workDirWritable").contains("diskFreeBytes");
        });
  }

  @Test
  void healthDelegatesToReadiness(@TempDir Path tmp) {
    JavalinTest.test(
        app(upController(tmp)),
        (server, client) -> {
          var resp = client.get("/health");
          assertThat(resp.code()).isEqualTo(200);
          assertThat(resp.body().string()).contains("workDirWritable");
        });
  }

  @Test
  void readinessReturns503WhenAnyCheckDown(@TempDir Path tmp) {
    var workers =
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    // disk threshold so high it cannot pass
    var hc = new HealthCheck(new JobRegistry(), workers, tmp, Long.MAX_VALUE);
    var controller = new HealthController(hc);
    JavalinTest.test(
        app(controller),
        (server, client) -> {
          var resp = client.get("/health/ready");
          assertThat(resp.code()).isEqualTo(503);
          assertThat(resp.body().string()).contains("\"status\":\"DOWN\"");
        });
  }

  @Test
  void livenessReturns503DuringShutdown(@TempDir Path tmp) {
    ShutdownState.beginShutdown();
    JavalinTest.test(
        app(upController(tmp)),
        (server, client) -> {
          var resp = client.get("/health/live");
          assertThat(resp.code()).isEqualTo(503);
          assertThat(resp.body().string()).contains("SHUTTING_DOWN");
        });
  }

  @Test
  void readinessReturns503DuringShutdown(@TempDir Path tmp) {
    ShutdownState.beginShutdown();
    JavalinTest.test(
        app(upController(tmp)),
        (server, client) -> {
          var resp = client.get("/health/ready");
          assertThat(resp.code()).isEqualTo(503);
          assertThat(resp.body().string()).contains("SHUTTING_DOWN");
        });
  }
}
