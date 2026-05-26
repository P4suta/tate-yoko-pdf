package dev.sakashita.tateyokopdf.web.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkDirsTest {

  // Deliberately passes null to a non-null param to exercise the runtime guard.
  @Test
  @SuppressWarnings("NullAway")
  void deleteQuietlyHandlesNull() {
    assertThatNoException().isThrownBy(() -> WorkDirs.deleteQuietly(null));
  }

  @Test
  void deleteQuietlyHandlesNonExistentPath(@TempDir Path tmp) {
    assertThatNoException().isThrownBy(() -> WorkDirs.deleteQuietly(tmp.resolve("does-not-exist")));
  }

  @Test
  void deleteQuietlyRemovesFlatDirectory(@TempDir Path tmp) throws Exception {
    Path dir = Files.createDirectory(tmp.resolve("flat"));
    Files.writeString(dir.resolve("a.txt"), "a");
    Files.writeString(dir.resolve("b.txt"), "b");
    WorkDirs.deleteQuietly(dir);
    assertThat(Files.exists(dir)).isFalse();
  }

  @Test
  void deleteQuietlyRemovesDeepTree(@TempDir Path tmp) throws Exception {
    Path root = Files.createDirectory(tmp.resolve("root"));
    Path nested = Files.createDirectories(root.resolve("a/b/c/d"));
    Files.writeString(nested.resolve("leaf.txt"), "x");
    WorkDirs.deleteQuietly(root);
    assertThat(Files.exists(root)).isFalse();
  }
}
