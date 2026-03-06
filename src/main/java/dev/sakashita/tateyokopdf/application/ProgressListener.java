package dev.sakashita.tateyokopdf.application;

public interface ProgressListener {

    void onStart(int totalSpreads);

    void onSpreadComplete(int currentSpread, int totalSpreads);

    void onComplete(long elapsedMillis);
}
