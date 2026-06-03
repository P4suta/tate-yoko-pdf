package dev.sakashita.tateyokopdf.domain.model;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.Validators;
import java.util.Objects;

public sealed interface PagePairSpec {

  record Pair(int firstIndex, int secondIndex) implements PagePairSpec {
    public Pair {
      Validators.requireNonNegative(firstIndex, ErrorKind.PDF_INVALID_PAGE, "firstIndex");
      Validators.requireNonNegative(secondIndex, ErrorKind.PDF_INVALID_PAGE, "secondIndex");
    }
  }

  record Single(int pageIndex, SpreadHalf half) implements PagePairSpec {
    public Single {
      Validators.requireNonNegative(pageIndex, ErrorKind.PDF_INVALID_PAGE, "pageIndex");
      Objects.requireNonNull(half, "half");
    }

    /** A lone page on the reading-leading half (standalone cover or odd trailing page). */
    public Single(int pageIndex) {
      this(pageIndex, SpreadHalf.LEADING);
    }
  }
}
