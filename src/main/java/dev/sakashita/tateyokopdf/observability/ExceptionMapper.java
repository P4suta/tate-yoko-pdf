package dev.sakashita.tateyokopdf.observability;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

public final class ExceptionMapper {

  public record Mapping(
      ErrorKind kind,
      int httpStatus,
      int wsCloseCode,
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
        domain.kind(),
        template.httpStatus,
        template.wsCloseCode,
        template.cliExitCode,
        template.logLevel,
        safeUser,
        domain.technicalDetail());
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

  private record Template(int httpStatus, int wsCloseCode, int cliExitCode, Level logLevel) {}

  private static Map<ErrorKind, Template> buildTable() {
    Map<ErrorKind, Template> m = new EnumMap<>(ErrorKind.class);
    m.put(ErrorKind.PDF_CORRUPTED, new Template(400, 4400, 65, Level.WARN));
    m.put(ErrorKind.PDF_PASSWORD_PROTECTED, new Template(400, 4400, 77, Level.WARN));
    m.put(ErrorKind.PDF_TOO_LARGE, new Template(413, 4413, 65, Level.WARN));
    m.put(ErrorKind.PDF_NOT_FOUND, new Template(404, 4404, 66, Level.WARN));
    m.put(ErrorKind.PDF_INVALID_PAGE, new Template(400, 4400, 65, Level.WARN));
    m.put(ErrorKind.PDF_WRITE_FAILED, new Template(500, 4500, 73, Level.ERROR));
    m.put(ErrorKind.UPLOAD_INVALID, new Template(400, 4400, 65, Level.WARN));
    m.put(ErrorKind.UPLOAD_EMPTY, new Template(400, 4400, 65, Level.WARN));
    m.put(ErrorKind.JOB_NOT_FOUND, new Template(404, 4404, 66, Level.WARN));
    m.put(ErrorKind.JOB_EXPIRED, new Template(410, 4410, 65, Level.WARN));
    m.put(ErrorKind.JOB_OUTPUT_GONE, new Template(410, 4410, 73, Level.WARN));
    m.put(ErrorKind.INVALID_PARAMETER, new Template(400, 4400, 64, Level.WARN));
    m.put(ErrorKind.OUT_OF_MEMORY, new Template(503, 4500, 137, Level.ERROR));
    m.put(ErrorKind.INTERNAL, new Template(500, 4500, 70, Level.ERROR));
    return m;
  }
}
