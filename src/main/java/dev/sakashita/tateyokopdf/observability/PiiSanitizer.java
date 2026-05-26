package dev.sakashita.tateyokopdf.observability;

import java.nio.file.Path;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class PiiSanitizer {

  private static final Pattern ABSOLUTE_PATH =
      Pattern.compile("(?:[A-Za-z]:)?[/\\\\][\\w\\-./\\\\]{3,}");

  private PiiSanitizer() {}

  public static String basenameOnly(Path path) {
    Path name = path.getFileName();
    return name == null ? path.toString() : name.toString();
  }

  public static String maskAbsolutePaths(@Nullable String message) {
    if (message == null || message.isEmpty()) {
      return "";
    }
    return ABSOLUTE_PATH.matcher(message).replaceAll("<path>");
  }
}
