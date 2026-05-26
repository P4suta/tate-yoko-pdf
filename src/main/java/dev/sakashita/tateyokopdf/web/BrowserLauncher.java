package dev.sakashita.tateyokopdf.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrowserLauncher {

  private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

  /**
   * Test seam: replace to intercept process launching. Default constructs a real {@link
   * ProcessBuilder} via {@link #defaultProcessBuilderFactory}.
   */
  static volatile Function<List<String>, ProcessBuilder> processBuilderFactory =
      defaultProcessBuilderFactory();

  /** Test seam: replace to override the {@code TATE_YOKO_NO_BROWSER} env lookup. */
  static volatile Supplier<String> noBrowserEnvSupplier =
      () -> System.getenv("TATE_YOKO_NO_BROWSER");

  private BrowserLauncher() {}

  public static void open(URI uri) {
    String envValue = Objects.requireNonNullElse(noBrowserEnvSupplier.get(), "false");
    if (Boolean.parseBoolean(envValue)) {
      log.info("TATE_YOKO_NO_BROWSER=true → skipping browser launch. Open manually: {}", uri);
      return;
    }

    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    List<String> command;
    if (os.contains("mac") || os.contains("darwin")) {
      command = List.of("open", uri.toString());
    } else if (os.contains("win")) {
      command = List.of("rundll32", "url.dll,FileProtocolHandler", uri.toString());
    } else {
      command = List.of("xdg-open", uri.toString());
    }

    try {
      processBuilderFactory.apply(command).inheritIO().start();
      log.info("Opened browser: {}", uri);
    } catch (Exception e) {
      log.warn("Failed to launch browser ({}). Open manually: {}", e.getMessage(), uri);
    }
  }

  static Function<List<String>, ProcessBuilder> defaultProcessBuilderFactory() {
    return ProcessBuilder::new;
  }
}
