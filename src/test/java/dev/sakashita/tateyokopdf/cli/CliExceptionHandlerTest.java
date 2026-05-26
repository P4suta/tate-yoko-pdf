package dev.sakashita.tateyokopdf.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class CliExceptionHandlerTest {

  @CommandLine.Command(name = "test-cmd")
  static final class NoopCommand implements Runnable {
    @Override
    public void run() {}
  }

  private static int dispatch(Exception ex, boolean verbose, ByteArrayOutputStream errBuffer) {
    var err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);
    var handler = new CliExceptionHandler(() -> verbose, err);
    var cmd = new CommandLine(new NoopCommand());
    return handler.handleExecutionException(ex, cmd, cmd.parseArgs());
  }

  @Test
  void corruptedPdfExitsWithInputDataCode() {
    var buf = new ByteArrayOutputStream();
    int code = dispatch(SpreadException.of(ErrorKind.PDF_CORRUPTED), false, buf);
    assertThat(code).isEqualTo(CliExitCodes.INPUT_DATA);
    assertThat(buf.toString(StandardCharsets.UTF_8)).contains("PDF_CORRUPTED");
  }

  @Test
  void passwordProtectedExitsWithPasswordCode() {
    var buf = new ByteArrayOutputStream();
    int code = dispatch(SpreadException.of(ErrorKind.PDF_PASSWORD_PROTECTED), false, buf);
    assertThat(code).isEqualTo(CliExitCodes.PASSWORD);
  }

  @Test
  void notFoundExitsWithInputNotFoundCode() {
    var buf = new ByteArrayOutputStream();
    int code = dispatch(SpreadException.of(ErrorKind.PDF_NOT_FOUND), false, buf);
    assertThat(code).isEqualTo(CliExitCodes.INPUT_NOTFOUND);
  }

  @Test
  void internalErrorExitsWithInternalCode() {
    var buf = new ByteArrayOutputStream();
    int code = dispatch(new RuntimeException("anything"), false, buf);
    assertThat(code).isEqualTo(CliExitCodes.INTERNAL);
  }

  @Test
  void verboseAppendsTechnicalDetailAndStackTrace() {
    var buf = new ByteArrayOutputStream();
    var ex =
        SpreadException.withDetail(
            ErrorKind.PDF_WRITE_FAILED, "destination=/some/path", new RuntimeException("io"));
    dispatch(ex, true, buf);
    String out = buf.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("PDF_WRITE_FAILED");
    assertThat(out).contains("detail:");
    assertThat(out).contains("destination=/some/path");
    assertThat(out).contains("RuntimeException");
  }

  @Test
  void nonVerboseOmitsStackTrace() {
    var buf = new ByteArrayOutputStream();
    var ex =
        SpreadException.withDetail(
            ErrorKind.PDF_WRITE_FAILED, "destination=/x", new RuntimeException("io"));
    dispatch(ex, false, buf);
    String out = buf.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("PDF_WRITE_FAILED");
    assertThat(out).doesNotContain("detail:");
  }
}
