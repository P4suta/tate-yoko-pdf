package dev.sakashita.tateyokopdf.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
@ResourceLock(value = "BrowserLauncher.processBuilderFactory", mode = ResourceAccessMode.READ_WRITE)
final class BrowserLauncherTest {

  private final Function<List<String>, ProcessBuilder> originalFactory =
      BrowserLauncher.processBuilderFactory;
  private final java.util.function.Supplier<String> originalEnvSupplier =
      BrowserLauncher.noBrowserEnvSupplier;
  private final String originalOsName = System.getProperty("os.name");

  @AfterEach
  void restore() {
    BrowserLauncher.processBuilderFactory = originalFactory;
    BrowserLauncher.noBrowserEnvSupplier = originalEnvSupplier;
    if (originalOsName == null) {
      System.clearProperty("os.name");
    } else {
      System.setProperty("os.name", originalOsName);
    }
  }

  @Test
  void linuxUsesXdgOpen() {
    runWithOs("Linux", List.of("xdg-open", "http://127.0.0.1:1234/"));
  }

  @Test
  void macUsesOpen() {
    runWithOs("Mac OS X", List.of("open", "http://127.0.0.1:1234/"));
  }

  @Test
  void windowsUsesRundll32() {
    runWithOs(
        "Windows 11", List.of("rundll32", "url.dll,FileProtocolHandler", "http://127.0.0.1:1234/"));
  }

  @Test
  void processStartFailureIsSwallowed() {
    BrowserLauncher.processBuilderFactory =
        cmd -> new ProcessBuilder("this-binary-does-not-exist-" + System.nanoTime());
    BrowserLauncher.noBrowserEnvSupplier = () -> "false";
    System.setProperty("os.name", "Linux");
    assertThatNoException()
        .isThrownBy(() -> BrowserLauncher.open(URI.create("http://127.0.0.1:1234/")));
  }

  @Test
  void noBrowserEnvTrueSkipsLaunch() {
    var captured = new AtomicReference<List<String>>();
    BrowserLauncher.processBuilderFactory =
        cmd -> {
          captured.set(cmd);
          return new ProcessBuilder("true");
        };
    BrowserLauncher.noBrowserEnvSupplier = () -> "true";
    BrowserLauncher.open(URI.create("http://127.0.0.1:1234/"));
    assertThat(captured.get()).isNull();
  }

  private static void runWithOs(String osName, List<String> expectedCommand) {
    var captured = new AtomicReference<List<String>>();
    BrowserLauncher.processBuilderFactory =
        cmd -> {
          captured.set(cmd);
          return new ProcessBuilder("true");
        };
    BrowserLauncher.noBrowserEnvSupplier = () -> "false";
    System.setProperty("os.name", osName);
    BrowserLauncher.open(URI.create("http://127.0.0.1:1234/"));
    assertThat(captured.get()).isEqualTo(expectedCommand);
  }
}
