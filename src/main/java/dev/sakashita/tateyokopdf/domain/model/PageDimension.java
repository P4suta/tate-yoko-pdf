package dev.sakashita.tateyokopdf.domain.model;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.Validators;

public record PageDimension(float widthPt, float heightPt) {

  public PageDimension {
    Validators.requirePositive(widthPt, ErrorKind.INVALID_PARAMETER, "widthPt");
    Validators.requirePositive(heightPt, ErrorKind.INVALID_PARAMETER, "heightPt");
  }

  public static PageDimension max(PageDimension a, PageDimension b) {
    return new PageDimension(Math.max(a.widthPt, b.widthPt), Math.max(a.heightPt, b.heightPt));
  }
}
