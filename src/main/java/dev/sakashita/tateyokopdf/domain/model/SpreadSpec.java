package dev.sakashita.tateyokopdf.domain.model;

import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.Validators;

public record SpreadSpec(float widthPt, float heightPt) {

  public SpreadSpec {
    Validators.requirePositive(widthPt, ErrorKind.INVALID_PARAMETER, "widthPt");
    Validators.requirePositive(heightPt, ErrorKind.INVALID_PARAMETER, "heightPt");
  }
}
