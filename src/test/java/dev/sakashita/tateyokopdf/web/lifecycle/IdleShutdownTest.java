package dev.sakashita.tateyokopdf.web.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.sakashita.tateyokopdf.testfixtures.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class IdleShutdownTest {

  @Test
  void invokesShutdownAfterIdleTimeout() {
    var fires = new AtomicInteger();
    var clock = TestClock.at(Instant.parse("2026-01-01T00:00:00Z"));
    var idle =
        new IdleShutdown(
            Duration.ofMillis(50), Duration.ofMillis(20), fires::incrementAndGet, clock);
    idle.start();
    try {
      // advance time past the idle threshold
      clock.advance(Duration.ofMillis(100));
      await().atMost(2, TimeUnit.SECONDS).until(() -> fires.get() >= 1);
      assertThat(fires.get()).isGreaterThanOrEqualTo(1);
    } finally {
      idle.stop();
    }
  }

  @Test
  void doesNotShutdownWhileConnectionsActive() {
    var fires = new AtomicInteger();
    var clock = TestClock.at(Instant.parse("2026-01-01T00:00:00Z"));
    var idle =
        new IdleShutdown(
            Duration.ofMillis(50), Duration.ofMillis(20), fires::incrementAndGet, clock);
    idle.onConnect();
    idle.start();
    try {
      clock.advance(Duration.ofMillis(500));
      // give scheduler time to attempt several ticks
      try {
        Thread.sleep(150);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      assertThat(fires.get()).isZero();
    } finally {
      idle.stop();
    }
  }

  @Test
  void disconnectResetsTheIdleTimer() {
    var fires = new AtomicInteger();
    var clock = TestClock.at(Instant.parse("2026-01-01T00:00:00Z"));
    var idle =
        new IdleShutdown(
            Duration.ofMillis(50), Duration.ofMillis(20), fires::incrementAndGet, clock);
    idle.onConnect();
    idle.onDisconnect(); // resets lastDisconnect = now()
    idle.start();
    try {
      clock.advance(Duration.ofMillis(100));
      await().atMost(2, TimeUnit.SECONDS).until(() -> fires.get() >= 1);
    } finally {
      idle.stop();
    }
  }
}
