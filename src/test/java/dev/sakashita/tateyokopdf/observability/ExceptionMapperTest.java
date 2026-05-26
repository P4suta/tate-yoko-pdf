package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

final class ExceptionMapperTest {

  @Test
  void mapsSpreadExceptionPreservingKind() {
    var ex = SpreadException.of(ErrorKind.PDF_CORRUPTED);
    var mapping = ExceptionMapper.map(ex);
    assertThat(mapping.kind()).isEqualTo(ErrorKind.PDF_CORRUPTED);
    assertThat(mapping.httpStatus()).isEqualTo(400);
    assertThat(mapping.logLevel()).isEqualTo(Level.WARN);
    assertThat(mapping.cliExitCode()).isEqualTo(65);
  }

  @Test
  void mapsIllegalArgumentExceptionToInvalidParameter() {
    var mapping = ExceptionMapper.map(new IllegalArgumentException("bad"));
    assertThat(mapping.kind()).isEqualTo(ErrorKind.INVALID_PARAMETER);
    assertThat(mapping.httpStatus()).isEqualTo(400);
    assertThat(mapping.technicalDetail()).isEqualTo("bad");
  }

  @Test
  void mapsIllegalArgumentExceptionWithNullMessageToClassName() {
    var mapping = ExceptionMapper.map(new IllegalArgumentException());
    assertThat(mapping.kind()).isEqualTo(ErrorKind.INVALID_PARAMETER);
    assertThat(mapping.technicalDetail()).isEqualTo("IllegalArgumentException");
  }

  @Test
  void mapsOutOfMemoryErrorToDedicatedKind() {
    var mapping = ExceptionMapper.map(new OutOfMemoryError("heap"));
    assertThat(mapping.kind()).isEqualTo(ErrorKind.OUT_OF_MEMORY);
    assertThat(mapping.httpStatus()).isEqualTo(503);
    assertThat(mapping.cliExitCode()).isEqualTo(137);
    assertThat(mapping.logLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  void mapsArbitraryIOExceptionToInternal() {
    var mapping = ExceptionMapper.map(new IOException("disk"));
    assertThat(mapping.kind()).isEqualTo(ErrorKind.INTERNAL);
    assertThat(mapping.httpStatus()).isEqualTo(500);
    assertThat(mapping.logLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  void mapsArbitraryThrowableToInternal() {
    var mapping = ExceptionMapper.map(new RuntimeException("boom"));
    assertThat(mapping.kind()).isEqualTo(ErrorKind.INTERNAL);
  }

  @Test
  void maskAbsolutePathsInUserMessage() {
    var ex =
        new SpreadException(
            ErrorKind.PDF_CORRUPTED, "Failed to load /tmp/secret/path/to/file.pdf", null, null);
    var mapping = ExceptionMapper.map(ex);
    assertThat(mapping.userMessage()).doesNotContain("/tmp/secret");
    assertThat(mapping.userMessage()).contains("<path>");
  }

  @Test
  void carriesTechnicalDetailThrough() {
    var ex = SpreadException.withDetail(ErrorKind.PDF_NOT_FOUND, "path=/some/file.pdf", null);
    var mapping = ExceptionMapper.map(ex);
    assertThat(mapping.technicalDetail()).isEqualTo("path=/some/file.pdf");
  }
}
