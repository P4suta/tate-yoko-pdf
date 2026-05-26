package dev.sakashita.tateyokopdf.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class SpreadCommandTest {

  @Test
  void helpReturnsZero() {
    int code = execute("--help");
    assertThat(code).isZero();
  }

  @Test
  void versionReturnsZero() {
    int code = execute("--version");
    assertThat(code).isZero();
  }

  @Test
  void noArgsReturnsUsageError() {
    int code = execute();
    assertThat(code).isEqualTo(2); // picocli default USAGE
  }

  @Test
  void validPdfConvertsSuccessfully(@TempDir Path tmp) throws Exception {
    Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 4);
    Path output = tmp.resolve("out.pdf");
    int code = execute(input.toString(), "-o", output.toString());
    assertThat(code).isZero();
    assertThat(Files.exists(output)).isTrue();
    assertThat(Files.size(output)).isPositive();
  }

  @Test
  void brokenPdfReturnsNonZero(@TempDir Path tmp) throws Exception {
    Path garbage = PdfFixtures.corruptedHeader(tmp, "bad.pdf");
    int code = execute(garbage.toString(), "-o", tmp.resolve("out.pdf").toString());
    assertThat(code).isNotZero();
  }

  @Test
  void missingInputReturnsNonZero(@TempDir Path tmp) {
    int code = execute(tmp.resolve("missing.pdf").toString());
    assertThat(code).isNotZero();
  }

  @Test
  void coverSingleFlagAccepted(@TempDir Path tmp) throws Exception {
    Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 5);
    Path output = tmp.resolve("out.pdf");
    int code = execute(input.toString(), "-o", output.toString(), "--cover-single");
    assertThat(code).isZero();
    assertThat(Files.exists(output)).isTrue();
  }

  @Test
  void ltrDirectionAccepted(@TempDir Path tmp) throws Exception {
    Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 2);
    Path output = tmp.resolve("out.pdf");
    int code = execute(input.toString(), "-o", output.toString(), "-d", "LTR");
    assertThat(code).isZero();
  }

  @Test
  void unknownDirectionRejected(@TempDir Path tmp) throws Exception {
    Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 2);
    int code = execute(input.toString(), "-d", "WTF");
    assertThat(code).isNotZero();
  }

  private static int execute(String... args) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    var oldOut = System.out;
    var oldErr = System.err;
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    try {
      return new CommandLine(new SpreadCommand()).execute(args);
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }
}
