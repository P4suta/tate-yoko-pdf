package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock(value = "FatalUncaughtHandler.exitHook", mode = ResourceAccessMode.READ_WRITE)
final class FatalUncaughtHandlerTest {

  private final Consumer<Integer> originalExit = FatalUncaughtHandler.exitHook;
  private final Thread.UncaughtExceptionHandler originalDefault =
      Thread.getDefaultUncaughtExceptionHandler();

  @AfterEach
  void restore() {
    FatalUncaughtHandler.exitHook = originalExit;
    Thread.setDefaultUncaughtExceptionHandler(originalDefault);
  }

  @Test
  void installSetsDefaultHandler() {
    FatalUncaughtHandler.install();
    assertThat(Thread.getDefaultUncaughtExceptionHandler())
        .isInstanceOf(FatalUncaughtHandler.class);
  }

  @Test
  void oomTriggersExit137() {
    AtomicInteger exit = new AtomicInteger(-1);
    FatalUncaughtHandler.exitHook = exit::set;
    new FatalUncaughtHandler().uncaughtException(Thread.currentThread(), new OutOfMemoryError("h"));
    assertThat(exit.get()).isEqualTo(137);
  }

  @Test
  void otherThrowableDoesNotExit() {
    AtomicInteger exit = new AtomicInteger(-1);
    FatalUncaughtHandler.exitHook = exit::set;
    new FatalUncaughtHandler()
        .uncaughtException(Thread.currentThread(), new RuntimeException("plain"));
    assertThat(exit.get()).isEqualTo(-1);
  }
}
