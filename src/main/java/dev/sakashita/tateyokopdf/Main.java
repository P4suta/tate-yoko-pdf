package dev.sakashita.tateyokopdf;

import dev.sakashita.tateyokopdf.cli.SpreadCommand;
import dev.sakashita.tateyokopdf.web.WebLauncher;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    configureLogging();
    if (args.length == 0) {
      new WebLauncher().run();
      return;
    }
    SpreadCommand.runCli(args);
  }

  private static void configureLogging() {
    String format = System.getenv("TATE_YOKO_LOG_FORMAT");
    if (format != null && format.equalsIgnoreCase("json")) {
      System.setProperty("logback.configurationFile", "logback-json.xml");
    }
  }
}
