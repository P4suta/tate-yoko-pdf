package dev.sakashita.tateyokopdf.application;

import dev.sakashita.tateyokopdf.domain.model.FirstPageMode;
import dev.sakashita.tateyokopdf.domain.strategy.CoverSinglePagination;
import dev.sakashita.tateyokopdf.domain.strategy.LeadingBlankPagination;
import dev.sakashita.tateyokopdf.domain.strategy.PaginationStrategy;
import dev.sakashita.tateyokopdf.domain.strategy.StandardPagination;

/**
 * Selects the {@link PaginationStrategy} for a {@link FirstPageMode}.
 *
 * <p>Strategy selection is an application-layer concern: callers obtain a strategy here rather than
 * instantiating a concrete one (enforced by the architecture tests).
 */
public final class PaginationStrategyFactory {

  private PaginationStrategyFactory() {}

  /**
   * Returns the pagination strategy that realises {@code mode}.
   *
   * @param mode how page 1 opens
   * @return the matching strategy
   */
  public static PaginationStrategy from(FirstPageMode mode) {
    return switch (mode) {
      case STANDARD -> new StandardPagination();
      case COVER -> new CoverSinglePagination();
      case LEADING_BLANK -> new LeadingBlankPagination();
    };
  }
}
