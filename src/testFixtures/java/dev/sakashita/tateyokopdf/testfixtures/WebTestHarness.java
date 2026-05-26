package dev.sakashita.tateyokopdf.testfixtures;

import dev.sakashita.tateyokopdf.web.WebLauncher;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.lifecycle.IdleShutdown;
import dev.sakashita.tateyokopdf.web.routes.JobController;
import dev.sakashita.tateyokopdf.web.routes.PageController;
import dev.sakashita.tateyokopdf.web.routes.ViewRenderer;
import dev.sakashita.tateyokopdf.web.routes.WebExceptionHandler;
import dev.sakashita.tateyokopdf.web.upload.UploadValidator;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);
    ViewRenderer renderer = new ViewRenderer(engine);
    JobRegistry registry = new JobRegistry();
    ExecutorService workers =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "tate-yoko-test-worker");
              t.setDaemon(true);
              return t;
            });
    PageController pages = new PageController(renderer);
    JobController jobs = new JobController(registry, renderer, workers, new UploadValidator());
    IdleShutdown idle =
        new IdleShutdown(Duration.ofHours(1), Duration.ofHours(1), () -> {}, Instant::now);
    WebExceptionHandler exHandler = new WebExceptionHandler(renderer);
    return WebLauncher.buildJavalin(pages, jobs, idle, exHandler, uploadBytes);
  }

  private static final class Defaults {
    static long uploadBytes() {
      return 500L * 1024 * 1024;
    }
  }
}
