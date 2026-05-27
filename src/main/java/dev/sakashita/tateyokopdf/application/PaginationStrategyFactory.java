package dev.sakashita.tateyokopdf.application;

import dev.sakashita.tateyokopdf.domain.strategy.CoverSinglePagination;
import dev.sakashita.tateyokopdf.domain.strategy.PaginationStrategy;
import dev.sakashita.tateyokopdf.domain.strategy.StandardPagination;

public final class PaginationStrategyFactory {

  private PaginationStrategyFactory() {}

  public static PaginationStrategy from(boolean coverSingle) {
    return coverSingle ? new CoverSinglePagination() : new StandardPagination();
  }
}
