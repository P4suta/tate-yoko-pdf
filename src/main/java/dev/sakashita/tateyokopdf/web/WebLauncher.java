package dev.sakashita.tateyokopdf.web;

import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.lifecycle.TempFileGc;
import dev.sakashita.tateyokopdf.web.lifecycle.WorkDirs;
import dev.sakashita.tateyokopdf.web.routes.JobController;
import dev.sakashita.tateyokopdf.web.routes.PageController;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebLauncher {

  private static final Logger log = LoggerFactory.getLogger(WebLauncher.class);

  private static final long MAX_UPLOAD_BYTES = 500L * 1024 * 1024;
  private static final Duration JOB_TTL = Duration.ofHours(1);
  private static final Duration GC_SWEEP_INTERVAL = Duration.ofMinutes(1);

  public void run() {
    String bind = System.getenv().getOrDefault("TATE_YOKO_BIND", "127.0.0.1");
    int port = resolvePort();

    TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);
    JobRegistry registry = new JobRegistry();
    PageController pages = new PageController(engine);
    JobController jobs = new JobController(registry, engine);

    TempFileGc gc = new TempFileGc(registry, JOB_TTL, GC_SWEEP_INTERVAL);
    gc.start();

    Javalin app =
        Javalin.create(
                config -> {
                  config.startup.showJavalinBanner = false;
                  config.http.maxRequestSize = MAX_UPLOAD_BYTES;
                  config.routes.get("/health", ctx -> ctx.result("OK"));
                  config.routes.get("/", pages::index);
                  config.routes.post("/jobs", jobs::submit);
                  config.routes.get("/jobs/{id}/result", jobs::showResult);
                  config.routes.get("/jobs/{id}/download", jobs::download);
                })
            .start(bind, port);
    int actualPort = app.port();

    URI browseTarget = URI.create("http://127.0.0.1:" + actualPort + "/");
    log.info("tate-yoko-pdf web running at {}", browseTarget);
    BrowserLauncher.open(browseTarget);

    var shutdown = new CountDownLatch(1);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("Shutting down web server");
                  try {
                    app.stop();
                  } finally {
                    gc.stop();
                    registry.drainAll().forEach(job -> WorkDirs.deleteQuietly(job.workDir()));
                    shutdown.countDown();
                  }
                },
                "tate-yoko-shutdown"));

    try {
      shutdown.await();
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private static int resolvePort() {
    String envPort = System.getenv("TATE_YOKO_PORT");
    if (envPort != null && !envPort.isBlank()) {
      return Integer.parseInt(envPort.trim());
    }
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to allocate a free port", e);
    }
  }
}
