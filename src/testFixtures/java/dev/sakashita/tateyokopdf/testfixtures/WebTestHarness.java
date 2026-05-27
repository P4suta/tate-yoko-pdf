package dev.sakashita.tateyokopdf.testfixtures;

import dev.sakashita.tateyokopdf.observability.HealthCheck;
import dev.sakashita.tateyokopdf.web.WebLauncher;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.lifecycle.IdleShutdown;
import dev.sakashita.tateyokopdf.web.observability.HealthController;
import dev.sakashita.tateyokopdf.web.routes.JobController;
import dev.sakashita.tateyokopdf.web.routes.WebExceptionHandler;
import dev.sakashita.tateyokopdf.web.upload.UploadValidator;
import io.javalin.Javalin;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thin builder around {@link WebLauncher#buildJavalin} for tests. Returns a configured but not
 * started Javalin app suitable for {@code JavalinTest.test(app, (server, client) -> ...)}.
 */
public final class WebTestHarness {

  private WebTestHarness() {}

  public static Javalin app() {
    return app(Defaults.uploadBytes());
  }

  public static Javalin app(long uploadBytes) {
    JobRegistry registry = new JobRegistry();
    ExecutorService workers =
        Executors.newFixedThreadPool(
            1,
            r -> {
              Thread t = new Thread(r, "tate-yoko-test-worker");
              t.setDaemon(true);
              return t;
            });
    JobController jobs = new JobController(registry, workers, new UploadValidator());
    IdleShutdown idle =
        new IdleShutdown(Duration.ofHours(1), Duration.ofHours(1), () -> {}, Instant::now);
    WebExceptionHandler exHandler = new WebExceptionHandler();
    HealthController health =
        new HealthController(new HealthCheck(registry::size, (ThreadPoolExecutor) workers));
    return WebLauncher.buildJavalin(jobs, idle, exHandler, health, uploadBytes);
  }

  private static final class Defaults {
    static long uploadBytes() {
      return 500L * 1024 * 1024;
    }
  }
}
