package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PiiSanitizerTest {

  @Test
  void basenameOnlyReturnsLastSegment() {
    assertThat(PiiSanitizer.basenameOnly(Path.of("/tmp/abc/def/file.pdf"))).isEqualTo("file.pdf");
  }

  @Test
  void basenameOnlyReturnsFullPathForBareFilename() {
    assertThat(PiiSanitizer.basenameOnly(Path.of("file.pdf"))).isEqualTo("file.pdf");
  }

  @Test
  void maskAbsolutePathsReplacesUnixPath() {
    String masked = PiiSanitizer.maskAbsolutePaths("opening /home/user/secret/book.pdf failed");
    assertThat(masked).contains("<path>").doesNotContain("/home/user");
  }

  @Test
  void maskAbsolutePathsReplacesWindowsPath() {
    String masked = PiiSanitizer.maskAbsolutePaths("opening C:\\Users\\me\\file.pdf");
    assertThat(masked).contains("<path>").doesNotContain("Users\\me");
  }

  @Test
  void maskAbsolutePathsTolerantOfNullOrEmpty() {
    assertThat(PiiSanitizer.maskAbsolutePaths(null)).isEmpty();
    assertThat(PiiSanitizer.maskAbsolutePaths("")).isEmpty();
  }

  @Test
  void maskAbsolutePathsLeavesPlainMessageAlone() {
    assertThat(PiiSanitizer.maskAbsolutePaths("nothing to mask here"))
        .isEqualTo("nothing to mask here");
  }
}
