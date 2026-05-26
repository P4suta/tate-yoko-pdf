package dev.sakashita.tateyokopdf;

import dev.sakashita.tateyokopdf.cli.SpreadCommand;
import dev.sakashita.tateyokopdf.web.WebLauncher;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    if (args.length == 0) {
      new WebLauncher().run();
      return;
    }
    SpreadCommand.runCli(args);
  }
}
