package dev.sakashita.tateyokopdf.web;

import dev.sakashita.tateyokopdf.observability.HealthCheck;
import dev.sakashita.tateyokopdf.observability.HealthController;
import dev.sakashita.tateyokopdf.observability.RequestTracingFilter;
import dev.sakashita.tateyokopdf.observability.ShutdownState;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.lifecycle.IdleShutdown;
import dev.sakashita.tateyokopdf.web.lifecycle.SingleInstanceLock;
import dev.sakashita.tateyokopdf.web.lifecycle.TempFileGc;
import dev.sakashita.tateyokopdf.web.lifecycle.WorkDirs;
import dev.sakashita.tateyokopdf.web.routes.JobController;
import dev.sakashita.tateyokopdf.web.routes.PageController;
import dev.sakashita.tateyokopdf.web.routes.ViewRenderer;
import dev.sakashita.tateyokopdf.web.routes.WebExceptionHandler;
import dev.sakashita.tateyokopdf.web.upload.UploadValidator;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebLauncher {

  private static final Logger log = LoggerFactory.getLogger(WebLauncher.class);

  private static final long MAX_UPLOAD_BYTES = 500L * 1024 * 1024;
  private static final Duration JOB_TTL = Duration.ofHours(1);
  private static final Duration GC_SWEEP_INTERVAL = Duration.ofMinutes(1);
  private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration IDLE_CHECK_INTERVAL = Duration.ofSeconds(10);
  private static final int WORKER_POOL_SIZE = 2;

  public void run() {
    SingleInstanceLock lock = new SingleInstanceLock();
    Optional<URI> existing = lock.findLiveInstance();
    if (existing.isPresent()) {
      URI url = existing.get();
      log.info("Existing instance detected at {}. Opening browser and exiting.", url);
      BrowserLauncher.open(url);
      return;
    }

    String bind = System.getenv().getOrDefault("TATE_YOKO_BIND", "127.0.0.1");
    int port = resolvePort();

    TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);
    ViewRenderer renderer = new ViewRenderer(engine);
    JobRegistry registry = new JobRegistry();
    ExecutorService workers =
        Executors.newFixedThreadPool(
            WORKER_POOL_SIZE,
            r -> {
              Thread t = new Thread(r, "tate-yoko-worker");
              t.setDaemon(true);
              return t;
            });

    PageController pages = new PageController(renderer);
    JobController jobs = new JobController(registry, renderer, workers, new UploadValidator());
    WebExceptionHandler exHandler = new WebExceptionHandler(renderer);
    HealthController health =
        new HealthController(new HealthCheck(registry, (ThreadPoolExecutor) workers));

    TempFileGc gc = new TempFileGc(registry, JOB_TTL, GC_SWEEP_INTERVAL);
    gc.start();

    IdleShutdown idle =
        new IdleShutdown(
            IDLE_TIMEOUT,
            IDLE_CHECK_INTERVAL,
            () -> System.exit(0),
            java.time.Instant::now,
            registry::hasRunningJobs);
    idle.start();

    Javalin app =
        buildJavalin(pages, jobs, idle, exHandler, health, MAX_UPLOAD_BYTES).start(bind, port);
    int actualPort = app.port();
    lock.claim(actualPort);

    URI browseTarget = URI.create("http://127.0.0.1:" + actualPort + "/");
    log.info("tate-yoko-pdf web running at {}", browseTarget);
    BrowserLauncher.open(browseTarget);

    var shutdown = new CountDownLatch(1);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("Shutting down web server");
                  ShutdownState.beginShutdown();
                  try {
                    app.stop();
                  } finally {
                    idle.stop();
                    gc.stop();
                    workers.shutdownNow();
                    try {
                      workers.awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    registry.drainAll().forEach(job -> WorkDirs.deleteQuietly(job.workDir()));
                    lock.release();
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

  public static Javalin buildJavalin(
      PageController pages,
      JobController jobs,
      IdleShutdown idle,
      WebExceptionHandler exHandler,
      HealthController health,
      long maxUploadBytes) {
    return Javalin.create(
        config -> {
          config.startup.showJavalinBanner = false;
          config.http.maxRequestSize = maxUploadBytes;
          config.routes.before(RequestTracingFilter::before);
          config.routes.after(RequestTracingFilter::after);
          config.routes.get("/health", health::health);
          config.routes.get("/health/live", health::liveness);
          config.routes.get("/health/ready", health::readiness);
          config.routes.get("/", pages::index);
          config.routes.post("/jobs", jobs::submit);
          config.routes.get("/jobs/{id}/progress", jobs::showProgress);
          config.routes.get("/jobs/{id}/result", jobs::showResult);
          config.routes.get("/jobs/{id}/download", jobs::download);
          config.routes.ws("/jobs/{id}/ws", ws -> ws.onConnect(jobs::onProgressWs));
          config.routes.ws(
              "/ws/keepalive",
              ws -> {
                ws.onConnect(ctx -> idle.onConnect());
                ws.onClose(ctx -> idle.onDisconnect());
              });
          config.routes.exception(SpreadException.class, exHandler::handleDomain);
          config.routes.exception(Exception.class, exHandler::handleUnknown);
          config.routes.error(404, exHandler::handleNotFound);
          config.routes.error(413, exHandler::handleTooLarge);
        });
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
