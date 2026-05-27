package dev.sakashita.tateyokopdf.observability;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide latch flipped by the shutdown hook so that {@code /health/ready} can start returning
 * 503 before Jetty actually stops accepting connections.
 */
public final class ShutdownState {

  private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);

  private ShutdownState() {}

  public static void beginShutdown() {
    SHUTTING_DOWN.set(true);
  }

  public static boolean isShuttingDown() {
    return SHUTTING_DOWN.get();
  }

  /** Test seam; clear the flag between tests. */
  public static void reset() {
    SHUTTING_DOWN.set(false);
  }
}
