package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock(value = "observability.ShutdownState", mode = ResourceAccessMode.READ_WRITE)
final class ShutdownStateTest {

  @AfterEach
  void reset() {
    ShutdownState.reset();
  }

  @Test
  void defaultsToNotShuttingDown() {
    assertThat(ShutdownState.isShuttingDown()).isFalse();
  }

  @Test
  void beginShutdownSetsTheFlag() {
    ShutdownState.beginShutdown();
    assertThat(ShutdownState.isShuttingDown()).isTrue();
  }

  @Test
  void resetClearsTheFlag() {
    ShutdownState.beginShutdown();
    ShutdownState.reset();
    assertThat(ShutdownState.isShuttingDown()).isFalse();
  }
}
