package dev.sakashita.tateyokopdf.web.job;

import dev.sakashita.tateyokopdf.application.ProgressListener;

public final class NoOpProgressListener implements ProgressListener {

  @Override
  public void onStart(int totalSpreads) {}

  @Override
  public void onSpreadComplete(int currentSpread, int totalSpreads) {}

  @Override
  public void onComplete(long elapsedMillis) {}
}
