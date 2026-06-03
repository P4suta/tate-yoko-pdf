package dev.sakashita.tateyokopdf.observability;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

/** Maps any throwable to a stable {@link ErrorKind}, a CLI exit code, and a safe user message. */
public final class ExceptionMapper {

  public record Mapping(
      ErrorKind kind,
      int cliExitCode,
      Level logLevel,
      String userMessage,
      @Nullable String technicalDetail) {}

  private static final Map<ErrorKind, Template> TABLE = buildTable();

  private ExceptionMapper() {}

  public static Mapping map(Throwable t) {
    SpreadException domain = asSpreadException(t);
    Template template =
        Objects.requireNonNull(TABLE.get(domain.kind()), () -> "no template for " + domain.kind());
    String safeUser = PiiSanitizer.maskAbsolutePaths(domain.userMessage());
    return new Mapping(
        domain.kind(), template.cliExitCode, template.logLevel, safeUser, domain.technicalDetail());
  }

  private static SpreadException asSpreadException(Throwable t) {
    if (t instanceof SpreadException spread) {
      return spread;
    }
    if (t instanceof IllegalArgumentException) {
      String detail = t.getMessage();
      return SpreadException.withDetail(
          ErrorKind.INVALID_PARAMETER, detail != null ? detail : t.getClass().getSimpleName(), t);
    }
    if (t instanceof IOException) {
      return SpreadException.of(ErrorKind.INTERNAL, t);
    }
    if (t instanceof OutOfMemoryError) {
      return SpreadException.of(ErrorKind.OUT_OF_MEMORY, t);
    }
    return SpreadException.of(ErrorKind.INTERNAL, t);
  }

  private record Template(int cliExitCode, Level logLevel) {}

  private static Map<ErrorKind, Template> buildTable() {
    Map<ErrorKind, Template> m = new EnumMap<>(ErrorKind.class);
    m.put(ErrorKind.PDF_CORRUPTED, new Template(65, Level.WARN));
    m.put(ErrorKind.PDF_PASSWORD_PROTECTED, new Template(77, Level.WARN));
    m.put(ErrorKind.PDF_NOT_FOUND, new Template(66, Level.WARN));
    m.put(ErrorKind.PDF_INVALID_PAGE, new Template(65, Level.WARN));
    m.put(ErrorKind.PDF_WRITE_FAILED, new Template(73, Level.ERROR));
    m.put(ErrorKind.INVALID_PARAMETER, new Template(64, Level.WARN));
    m.put(ErrorKind.OUT_OF_MEMORY, new Template(137, Level.ERROR));
    m.put(ErrorKind.INTERNAL, new Template(70, Level.ERROR));
    return m;
  }
}
