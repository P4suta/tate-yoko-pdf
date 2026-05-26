package dev.sakashita.tateyokopdf.web;

import dev.sakashita.tateyokopdf.web.routes.PageController;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebLauncher {

  private static final Logger log = LoggerFactory.getLogger(WebLauncher.class);

  public void run() {
    String bind = System.getenv().getOrDefault("TATE_YOKO_BIND", "127.0.0.1");
    int port = resolvePort();

    TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);
    PageController pages = new PageController(engine);

    Javalin app =
        Javalin.create(
                config -> {
                  config.startup.showJavalinBanner = false;
                  config.routes.get("/health", ctx -> ctx.result("OK"));
                  config.routes.get("/", pages::index);
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
                  app.stop();
                  shutdown.countDown();
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
