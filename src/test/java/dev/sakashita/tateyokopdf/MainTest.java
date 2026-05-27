package dev.sakashita.tateyokopdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MainTest {

  private static class Stub {
    final Map<String, String> sys = new HashMap<>();
    final Map<String, String> env = new HashMap<>();

    Stub set(String osName, String userHome) {
      sys.put("os.name", osName);
      sys.put("user.home", userHome);
      sys.put("java.io.tmpdir", userHome + "/tmp");
      return this;
    }

    Path resolve() {
      java.util.function.Function<String, @Nullable String> s = sys::get;
      java.util.function.Function<String, @Nullable String> e = env::get;
      return Main.resolveLogDir(s, e);
    }
  }

  @Test
  void linuxUsesXdgDataHomeWhenSet(@TempDir Path home) {
    var stub = new Stub().set("Linux", home.toString());
    stub.env.put("XDG_DATA_HOME", home.resolve("custom-xdg").toString());
    Path resolved = stub.resolve();
    assertThat(resolved).isEqualTo(home.resolve("custom-xdg/tate-yoko-pdf"));
    assertThat(resolved).exists().isDirectory();
  }

  @Test
  void linuxFallsBackToDotLocalShareWhenXdgUnset(@TempDir Path home) {
    var stub = new Stub().set("Linux", home.toString());
    Path resolved = stub.resolve();
    assertThat(resolved).isEqualTo(home.resolve(".local/share/tate-yoko-pdf"));
    assertThat(resolved).exists().isDirectory();
  }

  @Test
  void linuxIgnoresBlankXdg(@TempDir Path home) {
    var stub = new Stub().set("Linux", home.toString());
    stub.env.put("XDG_DATA_HOME", "   ");
    Path resolved = stub.resolve();
    assertThat(resolved).isEqualTo(home.resolve(".local/share/tate-yoko-pdf"));
  }

  @Test
  void macUsesLibraryLogs(@TempDir Path home) {
    var stub = new Stub().set("Mac OS X", home.toString());
    Path resolved = stub.resolve();
    assertThat(resolved).isEqualTo(home.resolve("Library/Logs/tate-yoko-pdf"));
    assertThat(resolved).exists().isDirectory();
  }

  @Test
  void windowsUsesAppdataWhenSet(@TempDir Path home) {
    var stub = new Stub().set("Windows 11", home.toString());
    stub.env.put("APPDATA", home.resolve("Roaming").toString());
    Path resolved = stub.resolve();
    assertThat(resolved).isEqualTo(home.resolve("Roaming/tate-yoko-pdf/logs"));
    assertThat(resolved).exists().isDirectory();
  }

  @Test
  void windowsFallsBackToAppDataRoamingWhenEnvUnset(@TempDir Path home) {
    var stub = new Stub().set("Windows 10", home.toString());
    Path resolved = stub.resolve();
    assertThat(resolved).isEqualTo(home.resolve("AppData/Roaming/tate-yoko-pdf/logs"));
  }
}
