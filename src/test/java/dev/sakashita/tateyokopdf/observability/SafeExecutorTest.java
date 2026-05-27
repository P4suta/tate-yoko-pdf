package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SafeExecutorTest {

  @Test
  void successPathPropagatesNoFailure() {
    List<SpreadException> failures = new ArrayList<>();
    Runnable guarded = new SafeExecutor().guarded(() -> {}, failures::add, "ctx");
    guarded.run();
    assertThat(failures).isEmpty();
  }

  @Test
  void spreadExceptionPassedThroughToSinkWithSameKind() {
    List<SpreadException> failures = new ArrayList<>();
    Runnable guarded =
        new SafeExecutor()
            .guarded(
                () -> {
                  throw SpreadException.of(ErrorKind.PDF_CORRUPTED);
                },
                failures::add,
                "ctx");
    guarded.run();
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0).kind()).isEqualTo(ErrorKind.PDF_CORRUPTED);
  }

  @Test
  void runtimeExceptionWrappedAsInternal() {
    List<SpreadException> failures = new ArrayList<>();
    Runnable guarded =
        new SafeExecutor()
            .guarded(
                () -> {
                  throw new IllegalStateException("boom");
                },
                failures::add,
                "ctx");
    guarded.run();
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0).kind()).isEqualTo(ErrorKind.INTERNAL);
  }

  @Test
  void outOfMemoryErrorReportsKindAndCallsExitHook() {
    List<SpreadException> failures = new ArrayList<>();
    AtomicInteger exitCode = new AtomicInteger(-1);
    Runnable guarded =
        new SafeExecutor(exitCode::set)
            .guarded(
                () -> {
                  throw new OutOfMemoryError("heap");
                },
                failures::add,
                "ctx");
    guarded.run();
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0).kind()).isEqualTo(ErrorKind.OUT_OF_MEMORY);
    assertThat(exitCode.get()).isEqualTo(137);
  }

  @Test
  void fatalThrowableMappedToInternalAndDoesNotExit() {
    List<SpreadException> failures = new ArrayList<>();
    AtomicInteger exitCode = new AtomicInteger(-1);
    Runnable guarded =
        new SafeExecutor(exitCode::set)
            .guarded(
                () -> {
                  throw new AssertionError("dead");
                },
                failures::add,
                "ctx");
    guarded.run();
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0).kind()).isEqualTo(ErrorKind.INTERNAL);
    assertThat(exitCode.get()).isEqualTo(-1);
  }

  @Test
  void sinkFailureDuringOomDoesNotHideExit() {
    AtomicInteger exitCode = new AtomicInteger(-1);
    Runnable guarded =
        new SafeExecutor(exitCode::set)
            .guarded(
                () -> {
                  throw new OutOfMemoryError("heap");
                },
                ex -> {
                  throw new RuntimeException("sink also died");
                },
                "ctx");
    guarded.run();
    assertThat(exitCode.get()).isEqualTo(137);
  }
}
