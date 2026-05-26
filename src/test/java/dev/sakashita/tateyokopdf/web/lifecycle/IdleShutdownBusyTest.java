package dev.sakashita.tateyokopdf.web.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class IdleShutdownBusyTest {

  @Test
  void doesNotShutdownWhileBusySupplierIsTrue() throws Exception {
    AtomicInteger fires = new AtomicInteger();
    AtomicBoolean busy = new AtomicBoolean(true);
    var clock = TestClock.at(Instant.parse("2026-01-01T00:00:00Z"));
    var idle =
        new IdleShutdown(
            Duration.ofMillis(50), Duration.ofMillis(20), fires::incrementAndGet, clock, busy::get);
    idle.start();
    try {
      clock.advance(Duration.ofMillis(500));
      Thread.sleep(150);
      assertThat(fires.get()).isZero();
    } finally {
      idle.stop();
    }
  }
}
