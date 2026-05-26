package dev.sakashita.tateyokopdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
final class MainTest {

  @AfterEach
  void cleanup() {
    System.clearProperty("logback.configurationFile");
  }

  @Test
  void nullFormatLeavesLogbackConfigUntouched() {
    Main.configureLogging(null);
    assertThat(System.getProperty("logback.configurationFile")).isNull();
  }

  @Test
  void emptyFormatLeavesLogbackConfigUntouched() {
    Main.configureLogging("");
    assertThat(System.getProperty("logback.configurationFile")).isNull();
  }

  @Test
  void jsonFormatSetsLogbackJsonConfig() {
    Main.configureLogging("json");
    assertThat(System.getProperty("logback.configurationFile")).isEqualTo("logback-json.xml");
  }

  @Test
  void jsonFormatIsCaseInsensitive() {
    Main.configureLogging("JSON");
    assertThat(System.getProperty("logback.configurationFile")).isEqualTo("logback-json.xml");
  }

  @Test
  void unknownFormatLeavesLogbackConfigUntouched() {
    Main.configureLogging("syslog");
    assertThat(System.getProperty("logback.configurationFile")).isNull();
  }
}
