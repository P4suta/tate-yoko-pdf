package dev.sakashita.tateyokopdf.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

final class OutputTargetTest {

  @Test
  void fileTargetHandsBackTheDestinationPath(@TempDir Path tmp) throws Exception {
    Path dest = tmp.resolve("out.pdf");
    AtomicReference<Path> handed = new AtomicReference<>();

    OutputTarget.file(dest)
        .write(
            p -> {
              handed.set(p);
              Files.writeString(p, "%PDF-1.7");
            });

    // A file target writes straight to the destination — no temp indirection.
    assertThat(handed.get()).isEqualTo(dest);
    assertThat(Files.readString(dest)).isEqualTo("%PDF-1.7");
  }

  @Test
  @ResourceLock(value = Resources.SYSTEM_OUT, mode = ResourceAccessMode.READ_WRITE)
  void stdoutTargetStreamsResultThenDeletesTemp() throws Exception {
    PrintStream original = System.out;
    var captured = new ByteArrayOutputStream();
    AtomicReference<Path> temp = new AtomicReference<>();
    try {
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
      OutputTarget.stdout()
          .write(
              p -> {
                temp.set(p);
                Files.writeString(p, "%PDF-stdout");
              });
    } finally {
      System.setOut(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8)).contains("%PDF-stdout");
    // The temp file the writer produced must not survive the stream-out.
    assertThat(Files.exists(temp.get())).isFalse();
  }
}
