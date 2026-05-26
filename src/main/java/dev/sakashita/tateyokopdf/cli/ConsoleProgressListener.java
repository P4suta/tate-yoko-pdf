package dev.sakashita.tateyokopdf.cli;

import dev.sakashita.tateyokopdf.application.ProgressListener;

public class ConsoleProgressListener implements ProgressListener {

  @Override
  public void onStart(int totalSpreads) {
    System.out.printf("Processing %d spreads...%n", totalSpreads);
  }

  @Override
  public void onSpreadComplete(int current, int total) {
    System.out.printf("\r[%d/%d] spreads completed", current, total);
  }

  @Override
  public void onComplete(long elapsedMillis) {
    System.out.printf("%nDone in %.1f seconds.%n", elapsedMillis / 1000.0);
  }
}
