package dev.sakashita.tateyokopdf.web.lifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks live WebSocket keepalive connections from open browser tabs. When the count drops to zero
 * and stays there for {@code idleTimeout}, runs a shutdown action (typically System.exit). This
 * gives the user a "close the tab and the app dies" experience without polling.
 */
public final class IdleShutdown {

  private static final Logger log = LoggerFactory.getLogger(IdleShutdown.class);

  private final Duration idleTimeout;
  private final Duration checkInterval;
  private final Runnable shutdownAction;
  private final Supplier<Instant> nowSupplier;
  private final BooleanSupplier busySupplier;
  private final ScheduledExecutorService scheduler;
  private final AtomicInteger active = new AtomicInteger(0);
  private final AtomicReference<Instant> lastDisconnect;
  // Guards against cold-start shutdown: with a short idleTimeout (5s) the
  // server could otherwise exit before Javalin finishes booting + the browser
  // launches + the SvelteKit page hydrates and opens /ws/keepalive. The
  // countdown only starts after the first real keepalive has connected.
  private final AtomicBoolean everConnected = new AtomicBoolean(false);
  private @Nullable ScheduledFuture<?> task;

  public IdleShutdown(Duration idleTimeout, Duration checkInterval, Runnable shutdownAction) {
    this(idleTimeout, checkInterval, shutdownAction, Instant::now, () -> false);
  }

  public IdleShutdown(
      Duration idleTimeout,
      Duration checkInterval,
      Runnable shutdownAction,
      Supplier<Instant> nowSupplier) {
    this(idleTimeout, checkInterval, shutdownAction, nowSupplier, () -> false);
  }

  public IdleShutdown(
      Duration idleTimeout,
      Duration checkInterval,
      Runnable shutdownAction,
      Supplier<Instant> nowSupplier,
      BooleanSupplier busySupplier) {
    this.idleTimeout = idleTimeout;
    this.checkInterval = checkInterval;
    this.shutdownAction = shutdownAction;
    this.nowSupplier = nowSupplier;
    this.busySupplier = busySupplier;
    this.lastDisconnect = new AtomicReference<>(nowSupplier.get());
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "tate-yoko-idle");
              t.setDaemon(true);
              return t;
            });
  }

  public void start() {
    task =
        scheduler.scheduleAtFixedRate(
            this::check, checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  public void stop() {
    if (task != null) {
      task.cancel(false);
    }
    scheduler.shutdownNow();
  }

  public void onConnect() {
    everConnected.set(true);
    int n = active.incrementAndGet();
    log.debug("keepalive open ({} active)", n);
  }

  public void onDisconnect() {
    int n = active.decrementAndGet();
    lastDisconnect.set(nowSupplier.get());
    log.debug("keepalive close ({} active)", n);
  }

  private void check() {
    try {
      if (!everConnected.get()) {
        return;
      }
      if (active.get() > 0) {
        return;
      }
      if (busySupplier.getAsBoolean()) {
        return;
      }
      Duration sinceLast = Duration.between(lastDisconnect.get(), nowSupplier.get());
      if (sinceLast.compareTo(idleTimeout) >= 0) {
        log.info("No browser keepalive for {}s; initiating idle shutdown", sinceLast.toSeconds());
        shutdownAction.run();
      }
    } catch (RuntimeException e) {
      log.warn("Idle shutdown check failed: {}", e.getMessage());
    }
  }
}
