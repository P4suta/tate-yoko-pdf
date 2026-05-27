package dev.sakashita.tateyokopdf.domain.exception;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

public final class Validators {

  private Validators() {}

  public static void require(boolean condition, ErrorKind kind, String technicalDetail) {
    if (!condition) {
      throw SpreadException.withDetail(kind, technicalDetail, null);
    }
  }

  public static <T> T requireNonNull(@Nullable T value, ErrorKind kind, String name) {
    if (value == null) {
      throw SpreadException.withDetail(kind, name + " must not be null", null);
    }
    return value;
  }

  public static float requirePositive(float value, ErrorKind kind, String name) {
    if (!(value > 0)) {
      throw SpreadException.withDetail(kind, name + " must be positive: " + value, null);
    }
    return value;
  }

  public static int requireNonNegative(int value, ErrorKind kind, String name) {
    if (value < 0) {
      throw SpreadException.withDetail(kind, name + " must be non-negative: " + value, null);
    }
    return value;
  }

  public static Path requireExists(Path path, ErrorKind kind) {
    if (!Files.exists(path)) {
      throw SpreadException.withDetail(kind, "path=" + path, null);
    }
    return path;
  }
}
