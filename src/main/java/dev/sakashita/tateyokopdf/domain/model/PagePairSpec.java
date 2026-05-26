package dev.sakashita.tateyokopdf.domain.model;

import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.Validators;

public sealed interface PagePairSpec {

  record Pair(int firstIndex, int secondIndex) implements PagePairSpec {
    public Pair {
      Validators.requireNonNegative(firstIndex, ErrorKind.PDF_INVALID_PAGE, "firstIndex");
      Validators.requireNonNegative(secondIndex, ErrorKind.PDF_INVALID_PAGE, "secondIndex");
    }
  }

  record Single(int pageIndex) implements PagePairSpec {
    public Single {
      Validators.requireNonNegative(pageIndex, ErrorKind.PDF_INVALID_PAGE, "pageIndex");
    }
  }
}
