package dev.sakashita.tateyokopdf.application;

import dev.sakashita.tateyokopdf.domain.model.FirstPageMode;
import dev.sakashita.tateyokopdf.domain.strategy.CoverSinglePagination;
import dev.sakashita.tateyokopdf.domain.strategy.LeadingBlankPagination;
import dev.sakashita.tateyokopdf.domain.strategy.PaginationStrategy;
import dev.sakashita.tateyokopdf.domain.strategy.StandardPagination;

public final class PaginationStrategyFactory {

  private PaginationStrategyFactory() {}

  public static PaginationStrategy from(FirstPageMode mode) {
    return switch (mode) {
      case STANDARD -> new StandardPagination();
      case COVER -> new CoverSinglePagination();
      case LEADING_BLANK -> new LeadingBlankPagination();
    };
  }
}
