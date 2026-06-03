package dev.sakashita.tateyokopdf;

import dev.sakashita.tateyokopdf.cli.SpreadCommand;
import dev.sakashita.tateyokopdf.observability.FatalUncaughtHandler;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    // Route every uncaught throwable (including OutOfMemoryError on background threads)
    // through a single handler so the process exits with a meaningful code instead of a
    // bare stack trace. Logging goes to stderr only — see logback.xml.
    Thread.setDefaultUncaughtExceptionHandler(new FatalUncaughtHandler());
    SpreadCommand.runCli(args);
  }
}
