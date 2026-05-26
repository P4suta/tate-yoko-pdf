package dev.sakashita.tateyokopdf.domain.model;

public record SpreadSpec(float widthPt, float heightPt) {

  public SpreadSpec {
    if (widthPt <= 0 || heightPt <= 0) {
      throw new IllegalArgumentException(
          "Spread dimensions must be positive: width=%f, height=%f".formatted(widthPt, heightPt));
    }
  }
}
