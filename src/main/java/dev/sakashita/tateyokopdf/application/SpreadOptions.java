package dev.sakashita.tateyokopdf.application;

import dev.sakashita.tateyokopdf.domain.model.ReadingDirection;
import java.nio.file.Path;
import java.util.Objects;

public record SpreadOptions(
    Path sourcePath, Path outputPath, ReadingDirection direction, boolean coverSingle) {
  public SpreadOptions {
    Objects.requireNonNull(sourcePath, "sourcePath must not be null");
    Objects.requireNonNull(outputPath, "outputPath must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
  }

  public static SpreadOptions withDefaults(Path sourcePath) {
    return new SpreadOptions(
        sourcePath, deriveOutputPath(sourcePath), ReadingDirection.DEFAULT, false);
  }

  private static Path deriveOutputPath(Path source) {
    String name = source.getFileName().toString();
    String output = name.replaceFirst("(?i)\\.pdf$", "_spread.pdf");
    return source.resolveSibling(output);
  }
}
