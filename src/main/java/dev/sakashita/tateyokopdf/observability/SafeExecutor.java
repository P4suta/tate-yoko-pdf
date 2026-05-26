package dev.sakashita.tateyokopdf.observability;

import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a {@link Runnable} so that any {@link Throwable} — including {@link OutOfMemoryError} — is
 * routed through the supplied {@code failureSink} (e.g. {@code WebProgressListener::fail}) and
 * never escapes silently. OOM additionally aborts the JVM with code 137 so the desktop instance can
 * be restarted rather than continue in an undefined state.
 */
public final class SafeExecutor {

  private static final Logger log = LoggerFactory.getLogger(SafeExecutor.class);

  /** Hook for tests; defaults to System.exit. */
  static volatile Consumer<Integer> exitHook = code -> System.exit(code);

  private SafeExecutor() {}

  public static Runnable guarded(
      Runnable body, Consumer<SpreadException> failureSink, String contextLabel) {
    return () -> {
      try {
        body.run();
      } catch (SpreadException e) {
        log.warn("[{}] guarded task failed [{}]: {}", contextLabel, e.kind(), e.userMessage(), e);
        failureSink.accept(e);
      } catch (RuntimeException e) {
        log.error("[{}] unexpected RuntimeException in guarded task", contextLabel, e);
        failureSink.accept(
            SpreadException.withDetail(ErrorKind.INTERNAL, e.getClass().getSimpleName(), e));
      } catch (OutOfMemoryError oom) {
        log.error("[{}] OOM in guarded task — exiting", contextLabel, oom);
        try {
          failureSink.accept(SpreadException.of(ErrorKind.OUT_OF_MEMORY, oom));
        } catch (Throwable suppress) {
          log.error("[{}] failureSink also failed during OOM handling", contextLabel, suppress);
        }
        exitHook.accept(137);
      } catch (Throwable t) {
        log.error("[{}] fatal Throwable in guarded task", contextLabel, t);
        try {
          failureSink.accept(SpreadException.withDetail(ErrorKind.INTERNAL, t.toString(), t));
        } catch (Throwable suppress) {
          log.error("[{}] failureSink also failed during fatal handling", contextLabel, suppress);
        }
      }
    };
  }
}
