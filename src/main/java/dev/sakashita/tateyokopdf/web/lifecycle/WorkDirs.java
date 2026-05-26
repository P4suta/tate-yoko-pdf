package dev.sakashita.tateyokopdf.web.lifecycle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorkDirs {

  private static final Logger log = LoggerFactory.getLogger(WorkDirs.class);

  private WorkDirs() {}

  public static void deleteQuietly(Path workDir) {
    if (workDir == null || !Files.exists(workDir)) {
      return;
    }
    try (var paths = Files.walk(workDir)) {
      paths
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException e) {
                  log.debug("Failed to delete {}: {}", p, e.getMessage());
                }
              });
    } catch (IOException e) {
      log.debug("Failed to walk {}: {}", workDir, e.getMessage());
    }
  }
}
