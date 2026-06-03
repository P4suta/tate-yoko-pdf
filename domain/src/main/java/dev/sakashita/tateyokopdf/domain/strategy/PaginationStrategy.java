package dev.sakashita.tateyokopdf.domain.strategy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import java.util.List;

/**
 * Pairs a document's pages into the sequence of spreads to emit.
 *
 * <p>Sealed over the three openings: {@link StandardPagination} (1·2, 3·4, …), {@link
 * CoverSinglePagination} (page 1 alone, then 2·3, …), and {@link LeadingBlankPagination} (an
 * implied blank, then 1·2, …). Obtain one via {@code PaginationStrategyFactory}.
 */
public sealed interface PaginationStrategy
    permits StandardPagination, CoverSinglePagination, LeadingBlankPagination {

  /**
   * Pairs the source pages into the spreads to emit, in output order.
   *
   * @param totalPages the number of pages in the source ({@code >= 0})
   * @return the spreads to emit
   */
  List<PagePairSpec> paginate(int totalPages);
}
