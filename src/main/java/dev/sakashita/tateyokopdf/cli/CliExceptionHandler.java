package dev.sakashita.tateyokopdf.cli;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.observability.ExceptionMapper;
import java.io.PrintStream;
import java.util.function.BooleanSupplier;
import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

public final class CliExceptionHandler implements IExecutionExceptionHandler {

  private final BooleanSupplier verboseSupplier;
  private final PrintStream err;

  public CliExceptionHandler(BooleanSupplier verboseSupplier) {
    this(verboseSupplier, System.err);
  }

  CliExceptionHandler(BooleanSupplier verboseSupplier, PrintStream err) {
    this.verboseSupplier = verboseSupplier;
    this.err = err;
  }

  @Override
  public int handleExecutionException(Exception ex, CommandLine cmd, ParseResult parseResult) {
    ExceptionMapper.Mapping mapping = ExceptionMapper.map(ex);
    err.println("Error[" + mapping.kind() + "]: " + mapping.userMessage());
    boolean verbose = verboseSupplier.getAsBoolean();
    if (verbose) {
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
