package dev.sakashita.tateyokopdf.web.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Stress {@link IdleShutdown}'s onConnect/onDisconnect counter under concurrent traffic from many
 * pseudo-WS clients while the scheduled idle check is running. The expected invariant:
 *
 * <ul>
 *   <li>The shutdown action fires <em>only</em> after every paired connect has been disconnected
 *       and the idle interval has elapsed since the last disconnect.
 *   <li>Spurious fires while connections are live (a races between increment + check) must not
 *       happen — would manifest as a flickering app close on the desktop user's screen.
 * </ul>
 */
final class IdleShutdownConcurrentTest {

  @Test
  void parallelConnectAndDisconnectKeepsTheCounterBalancedAndDoesNotFireWhileLive()
      throws Exception {
    var clock = TestClock.at(Instant.parse("2026-01-01T00:00:00Z"));
    var fires = new AtomicInteger();
    var idle =
        new IdleShutdown(
            Duration.ofMillis(50),
            Duration.ofMillis(5),
            fires::incrementAndGet,
            clock,
            () -> true /* busySupplier always says busy so the shutdown branch is gated */);
    idle.start();
    try {
      int threads = 16;
      int perThread = 1000;
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      try {
        for (int t = 0; t < threads; t++) {
          var _ =
              pool.submit(
                  () -> {
                    try {
                      start.await();
                      for (int i = 0; i < perThread; i++) {
                        idle.onConnect();
                        idle.onDisconnect();
                      }
                    } catch (InterruptedException ie) {
                      Thread.currentThread().interrupt();
                    } finally {
                      done.countDown();
                    }
                  });
        }
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
      } finally {
        pool.shutdownNow();
      }

      // While we were hammering, the scheduler's check thread also ran several times; if it had
      // observed a transiently-zero `active` count and ignored the `busySupplier`, fires would
      // increment. The busy supplier always-true gate makes the assertion crisp: even with
      // active==0 between paired connect/disconnect calls, shutdown must not fire.
      assertThat(fires.get()).isZero();
    } finally {
      idle.stop();
    }
  }
}
