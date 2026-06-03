package dev.sakashita.tateyokopdf.cli;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.observability.ExceptionMapper;
import java.io.PrintStream;
import java.util.function.BooleanSupplier;

/**
 * Turns an exception thrown by the conversion pipeline into a user-facing {@code Error[KIND]: ...}
 * line plus a sysexits-flavoured exit code. Kept framework-agnostic (no CLI-library types) so it
 * can be called from a plain try/catch in {@link SpreadCommand#run}.
 */
public final class CliExceptionHandler {

  private final BooleanSupplier verboseSupplier;
  private final PrintStream err;

  public CliExceptionHandler(BooleanSupplier verboseSupplier) {
    this(verboseSupplier, System.err);
  }

  CliExceptionHandler(BooleanSupplier verboseSupplier, PrintStream err) {
    this.verboseSupplier = verboseSupplier;
    this.err = err;
  }

  /** Reports {@code ex} to stderr and returns the exit code the process should terminate with. */
  public int handle(Throwable ex) {
    ExceptionMapper.Mapping mapping = ExceptionMapper.map(ex);
    err.println("Error[" + mapping.kind() + "]: " + mapping.userMessage());
    if (verboseSupplier.getAsBoolean()) {
      if (mapping.technicalDetail() != null) {
        err.println("  detail: " + mapping.technicalDetail());
      }
      ex.printStackTrace(err);
    } else if (mapping.kind() == ErrorKind.OUT_OF_MEMORY) {
      err.println("  ヒント: JVM の最大ヒープを増やしてください (例: -Xmx1g)");
    }
    return mapping.cliExitCode();
  }
}
