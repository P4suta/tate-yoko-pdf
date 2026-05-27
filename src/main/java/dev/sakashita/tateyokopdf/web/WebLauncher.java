package dev.sakashita.tateyokopdf.web;

import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.observability.HealthCheck;
import dev.sakashita.tateyokopdf.observability.ShutdownState;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.lifecycle.IdleShutdown;
import dev.sakashita.tateyokopdf.web.lifecycle.SingleInstanceLock;
import dev.sakashita.tateyokopdf.web.lifecycle.TempFileGc;
import dev.sakashita.tateyokopdf.web.lifecycle.WorkDirs;
import dev.sakashita.tateyokopdf.web.observability.HealthController;
import dev.sakashita.tateyokopdf.web.observability.RequestTracingFilter;
import dev.sakashita.tateyokopdf.web.routes.DownloadHandler;
import dev.sakashita.tateyokopdf.web.routes.JobController;
import dev.sakashita.tateyokopdf.web.routes.JobFactory;
import dev.sakashita.tateyokopdf.web.routes.WebExceptionHandler;
import dev.sakashita.tateyokopdf.web.upload.UploadValidator;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.io.InputStream;
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

    JobRegistry registry = new JobRegistry();
    ExecutorService workers =
        Executors.newFixedThreadPool(
            WORKER_POOL_SIZE,
            r -> {
              Thread t = new Thread(r, "tate-yoko-worker");
              t.setDaemon(true);
              return t;
            });

    JobController jobs =
        new JobController(
            registry,
            workers,
            new UploadValidator(),
            new JobFactory(registry),
            new DownloadHandler(registry));
    WebExceptionHandler exHandler = new WebExceptionHandler();
    HealthController health =
        new HealthController(new HealthCheck(registry::size, (ThreadPoolExecutor) workers));

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

    Javalin app = buildJavalin(jobs, idle, exHandler, health, MAX_UPLOAD_BYTES).start(bind, port);
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
      JobController jobs,
      IdleShutdown idle,
      WebExceptionHandler exHandler,
      HealthController health,
      long maxUploadBytes) {
    return Javalin.create(
        config -> {
          config.startup.showJavalinBanner = false;
          config.http.maxRequestSize = maxUploadBytes;
          // SvelteKit static assets (adapter-static) are staged into the JAR under
          // /static by the buildFrontend Gradle task; serve them from the root.
          config.staticFiles.add(
              it -> {
                it.hostedPath = "/";
                it.directory = "/static";
                it.location = Location.CLASSPATH;
              });
          config.routes.before(RequestTracingFilter::before);
          config.routes.after(RequestTracingFilter::after);
          config.routes.get("/api/health", health::health);
          config.routes.get("/api/health/live", health::liveness);
          config.routes.get("/api/health/ready", health::readiness);
          config.routes.post("/api/jobs", jobs::submit);
          config.routes.get("/api/jobs/{id}/download", jobs::download);
          config.routes.ws("/ws/jobs/{id}", ws -> ws.onConnect(jobs::onProgressWs));
          config.routes.ws(
              "/ws/keepalive",
              ws -> {
                ws.onConnect(ctx -> idle.onConnect());
                ws.onClose(ctx -> idle.onDisconnect());
              });
          config.routes.exception(SpreadException.class, exHandler::handleDomain);
          config.routes.exception(Exception.class, exHandler::handleUnknown);
          // Unmatched routes: API/WS paths get a JSON 404 from the exception handler;
          // anything else falls back to the SvelteKit SPA shell so client-side
          // routing can resolve it (e.g. /jobs/{id}).
          config.routes.error(404, ctx -> handleNotFound(ctx, exHandler));
          config.routes.error(413, exHandler::handleTooLarge);
        });
  }

  private static void handleNotFound(io.javalin.http.Context ctx, WebExceptionHandler exHandler) {
    String path = ctx.path();
    if (path.startsWith("/api/") || path.startsWith("/ws/")) {
      exHandler.handleNotFound(ctx);
      return;
    }
    serveSpaShell(ctx);
  }

  private static void serveSpaShell(io.javalin.http.Context ctx) {
    try (InputStream in = WebLauncher.class.getResourceAsStream("/static/index.html")) {
      if (in == null) {
        ctx.status(500);
        ctx.result("SPA shell missing from classpath");
        return;
      }
      ctx.status(200);
      ctx.contentType("text/html; charset=utf-8");
      ctx.result(in.readAllBytes());
    } catch (IOException e) {
      ctx.status(500);
      ctx.result("Failed to serve SPA shell: " + e.getMessage());
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
