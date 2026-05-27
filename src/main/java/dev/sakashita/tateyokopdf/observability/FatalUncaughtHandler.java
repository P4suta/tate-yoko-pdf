package dev.sakashita.tateyokopdf.observability;

import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link Thread.UncaughtExceptionHandler} for the whole JVM. Ensures any thread that dies
 * with an unhandled throwable still emits an ERROR log line (with traceId/jobId via MDC if
 * available) instead of vanishing into stderr — and exits 137 on OOM via the injected {@code exit}
 * consumer.
 */
public final class FatalUncaughtHandler implements Thread.UncaughtExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(FatalUncaughtHandler.class);

  private final IntConsumer exit;

  public FatalUncaughtHandler() {
    this(System::exit);
  }

  public FatalUncaughtHandler(IntConsumer exit) {
    this.exit = exit;
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    if (e instanceof OutOfMemoryError) {
      log.error("OOM on thread {} — exiting", t.getName(), e);
      exit.accept(137);
      return;
    }
    log.error("Uncaught exception on thread {}", t.getName(), e);
  }
}
