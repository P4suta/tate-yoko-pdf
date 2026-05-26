package dev.sakashita.tateyokopdf.domain.model;

public sealed interface PagePairSpec {

  record Pair(int firstIndex, int secondIndex) implements PagePairSpec {
    public Pair {
      if (firstIndex < 0 || secondIndex < 0) {
        throw new IllegalArgumentException("Page indices must be non-negative");
      }
    }
  }

  record Single(int pageIndex) implements PagePairSpec {
    public Single {
      if (pageIndex < 0) {
        throw new IllegalArgumentException("Page index must be non-negative");
      }
    }
  }
}
