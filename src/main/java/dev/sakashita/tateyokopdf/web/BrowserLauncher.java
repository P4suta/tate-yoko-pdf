package dev.sakashita.tateyokopdf.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens a URI in the host's default browser, with both the env-flag lookup and the {@link
 * ProcessBuilder} factory injectable so tests can drive the OS-detection branches without mutating
 * JVM-global state.
 */
public final class BrowserLauncher {

  private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

  private final Supplier<String> noBrowserEnvSupplier;
  private final Function<List<String>, ProcessBuilder> processBuilderFactory;

  public BrowserLauncher() {
    this(() -> System.getenv("TATE_YOKO_NO_BROWSER"), ProcessBuilder::new);
  }

  public BrowserLauncher(
      Supplier<String> noBrowserEnvSupplier,
      Function<List<String>, ProcessBuilder> processBuilderFactory) {
    this.noBrowserEnvSupplier = noBrowserEnvSupplier;
    this.processBuilderFactory = processBuilderFactory;
  }

  public void open(URI uri) {
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
}
