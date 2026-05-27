package dev.sakashita.tateyokopdf.observability;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Latch flipped by the shutdown hook so that {@code /health/ready} can start returning 503 before
 * Jetty actually stops accepting connections. Held as an instance so each test can construct its
 * own — no shared JVM-static state means no inter-test race against parallel JUnit execution.
 */
public final class ShutdownState {

  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  public void beginShutdown() {
    shuttingDown.set(true);
  }

  public boolean isShuttingDown() {
    return shuttingDown.get();
  }
}
