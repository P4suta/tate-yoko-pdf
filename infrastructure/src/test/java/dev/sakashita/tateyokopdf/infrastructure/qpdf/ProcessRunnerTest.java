package dev.sakashita.tateyokopdf.infrastructure.qpdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs({OS.LINUX, OS.MAC})
final class ProcessRunnerTest {

  private final ProcessRunner runner = new ProcessRunner();

  @Test
  void successfulProcessReportsExitZero() throws Exception {
    ProcessRunner.Result result = runner.run(List.of("/bin/true"), Duration.ofSeconds(10));
    assertThat(result.exitCode()).isZero();
  }

  @Test
  void failingProcessReportsNonZeroExit() throws Exception {
    ProcessRunner.Result result = runner.run(List.of("/bin/false"), Duration.ofSeconds(10));
    assertThat(result.exitCode()).isEqualTo(1);
  }

  @Test
  void mergedOutputCapturesStdoutAndStderr() throws Exception {
    ProcessRunner.Result result =
        runner.run(List.of("/bin/echo", "hello-runner"), Duration.ofSeconds(10));
    assertThat(result.exitCode()).isZero();
    assertThat(result.mergedOutput()).contains("hello-runner");
  }

  @Test
  void slowProcessIsKilledAndReportedAsTimeout() {
    assertThatThrownBy(() -> runner.run(List.of("/bin/sleep", "5"), Duration.ofMillis(100)))
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  void missingBinaryFailsWithIoException() {
    assertThatThrownBy(
            () -> runner.run(List.of("/definitely/not/a/binary"), Duration.ofSeconds(10)))
        .isInstanceOf(IOException.class);
  }
}
