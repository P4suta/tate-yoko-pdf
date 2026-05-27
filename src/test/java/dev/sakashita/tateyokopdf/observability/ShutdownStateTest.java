package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class ShutdownStateTest {

  @Test
  void defaultsToNotShuttingDown() {
    assertThat(new ShutdownState().isShuttingDown()).isFalse();
  }

  @Test
  void beginShutdownSetsTheFlag() {
    var state = new ShutdownState();
    state.beginShutdown();
    assertThat(state.isShuttingDown()).isTrue();
  }

  @Test
  void freshInstanceIsIndependent() {
    var a = new ShutdownState();
    var b = new ShutdownState();
    a.beginShutdown();
    assertThat(a.isShuttingDown()).isTrue();
    assertThat(b.isShuttingDown()).isFalse();
  }
}
