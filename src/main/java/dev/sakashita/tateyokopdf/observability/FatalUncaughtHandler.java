package dev.sakashita.tateyokopdf.observability;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link Thread.UncaughtExceptionHandler} for the whole JVM. Ensures any thread that dies
 * with an unhandled throwable still emits an ERROR log line (with traceId/jobId via MDC if
 * available) instead of vanishing into stderr — and exits 137 on OOM.
 */
public final class FatalUncaughtHandler implements Thread.UncaughtExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(FatalUncaughtHandler.class);

  /** Hook for tests; defaults to System.exit. */
  static volatile Consumer<Integer> exitHook = code -> System.exit(code);

  public static void install() {
    Thread.setDefaultUncaughtExceptionHandler(new FatalUncaughtHandler());
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    if (e instanceof OutOfMemoryError) {
      log.error("OOM on thread {} — exiting", t.getName(), e);
      exitHook.accept(137);
      return;
    }
    log.error("Uncaught exception on thread {}", t.getName(), e);
  }
}
