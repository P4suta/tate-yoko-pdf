package dev.sakashita.tateyokopdf.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(value = Resources.SYSTEM_OUT, mode = ResourceAccessMode.READ_WRITE)
final class ConsoleProgressListenerTest {

  @Test
  void onStartPrintsTotal() {
    String out = captureStdout(() -> new ConsoleProgressListener().onStart(7));
    assertThat(out).contains("Processing 7 spreads");
  }

  @Test
  void onSpreadCompleteUsesCarriageReturnForOverwrite() {
    String out =
        captureStdout(
            () -> {
              var l = new ConsoleProgressListener();
              l.onSpreadComplete(1, 4);
              l.onSpreadComplete(2, 4);
            });
    assertThat(out).startsWith("\r").contains("[1/4]").contains("[2/4]");
  }

  @Test
  void onCompletePrintsDurationWithOneDecimal() {
    String out = captureStdout(() -> new ConsoleProgressListener().onComplete(1500));
    assertThat(out).contains("Done in 1.5 seconds");
  }

  private static String captureStdout(Runnable body) {
    PrintStream original = System.out;
    var buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    try {
      body.run();
    } finally {
      System.setOut(original);
    }
    return buffer.toString(StandardCharsets.UTF_8);
  }
}
