package dev.sakashita.tateyokopdf.application;

import dev.sakashita.tateyokopdf.domain.model.FirstPageMode;
import dev.sakashita.tateyokopdf.domain.model.ReadingDirection;
import java.nio.file.Path;
import java.util.Objects;

public record SpreadOptions(
    Path sourcePath,
    Path outputPath,
    ReadingDirection direction,
    FirstPageMode firstPageMode,
    boolean pdfA) {
  public SpreadOptions {
    Objects.requireNonNull(sourcePath, "sourcePath must not be null");
    Objects.requireNonNull(outputPath, "outputPath must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
    Objects.requireNonNull(firstPageMode, "firstPageMode must not be null");
  }

  public static SpreadOptions withDefaults(Path sourcePath) {
    return new SpreadOptions(
        sourcePath,
        deriveOutputPath(sourcePath),
        ReadingDirection.DEFAULT,
        FirstPageMode.STANDARD,
        false);
  }

  private static Path deriveOutputPath(Path source) {
    Path file = source.getFileName();
    if (file == null) {
      // Path.getFileName() only returns null for roots like "/" or "C:\\", which
      // we never legitimately receive as input — guard explicitly.
      throw new IllegalArgumentException("source path has no file name: " + source);
    }
    String output = file.toString().replaceFirst("(?i)\\.pdf$", "_spread.pdf");
    return source.resolveSibling(output);
  }
}
