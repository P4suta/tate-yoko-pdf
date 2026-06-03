package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class FatalUncaughtHandlerTest {

  @Test
  void oomTriggersExit137() {
    AtomicInteger exit = new AtomicInteger(-1);
    new FatalUncaughtHandler(exit::set)
        .uncaughtException(Thread.currentThread(), new OutOfMemoryError("h"));
    assertThat(exit.get()).isEqualTo(137);
  }

  @Test
  void otherThrowableDoesNotExit() {
    AtomicInteger exit = new AtomicInteger(-1);
    new FatalUncaughtHandler(exit::set)
        .uncaughtException(Thread.currentThread(), new RuntimeException("plain"));
    assertThat(exit.get()).isEqualTo(-1);
  }
}
