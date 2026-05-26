package dev.sakashita.tateyokopdf;

import dev.sakashita.tateyokopdf.cli.SpreadCommand;
import dev.sakashita.tateyokopdf.web.WebLauncher;
import org.jspecify.annotations.Nullable;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    configureLogging(System.getenv("TATE_YOKO_LOG_FORMAT"));
    if (args.length == 0) {
      new WebLauncher().run();
      return;
    }
    SpreadCommand.runCli(args);
  }

  static void configureLogging(@Nullable String format) {
    if (format != null && format.equalsIgnoreCase("json")) {
      System.setProperty("logback.configurationFile", "logback-json.xml");
    }
  }
}
