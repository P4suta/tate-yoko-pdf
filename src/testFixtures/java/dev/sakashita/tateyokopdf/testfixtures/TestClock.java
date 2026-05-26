package dev.sakashita.tateyokopdf.testfixtures;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Mutable {@link Supplier}&lt;{@link Instant}&gt; for deterministic time tests. */
public final class TestClock implements Supplier<Instant> {

  private final AtomicReference<Instant> now;

  public TestClock(Instant initial) {
    this.now = new AtomicReference<>(initial);
  }

  public static TestClock at(Instant initial) {
    return new TestClock(initial);
  }

  public void advance(Duration delta) {
    now.updateAndGet(t -> t.plus(delta));
  }

  public void set(Instant t) {
    now.set(t);
  }

  @Override
  public Instant get() {
    return now.get();
  }
}
