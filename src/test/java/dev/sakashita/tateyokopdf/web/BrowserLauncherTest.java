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

/**
 * Drives the OS-detection branches of {@link BrowserLauncher} by injecting fake collaborators. The
 * {@code os.name} system property still has to be set/restored per test, so the SYSTEM_PROPERTIES
 * resource lock is retained — but the lock on {@code processBuilderFactory} is gone, since each
 * test now owns its own {@code BrowserLauncher} instance.
 */
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
final class BrowserLauncherTest {

  private final String originalOsName = System.getProperty("os.name");

  @AfterEach
  void restoreOs() {
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
    Function<List<String>, ProcessBuilder> bogus =
        cmd -> new ProcessBuilder("this-binary-does-not-exist-" + System.nanoTime());
    var launcher = new BrowserLauncher(() -> "false", bogus);
    System.setProperty("os.name", "Linux");
    assertThatNoException().isThrownBy(() -> launcher.open(URI.create("http://127.0.0.1:1234/")));
  }

  @Test
  void noBrowserEnvTrueSkipsLaunch() {
    var captured = new AtomicReference<List<String>>();
    var launcher =
        new BrowserLauncher(
            () -> "true",
            cmd -> {
              captured.set(cmd);
              return new ProcessBuilder("true");
            });
    launcher.open(URI.create("http://127.0.0.1:1234/"));
    assertThat(captured.get()).isNull();
  }

  private static void runWithOs(String osName, List<String> expectedCommand) {
    var captured = new AtomicReference<List<String>>();
    var launcher =
        new BrowserLauncher(
            () -> "false",
            cmd -> {
              captured.set(cmd);
              return new ProcessBuilder("true");
            });
    System.setProperty("os.name", osName);
    launcher.open(URI.create("http://127.0.0.1:1234/"));
    assertThat(captured.get()).isEqualTo(expectedCommand);
  }
}
