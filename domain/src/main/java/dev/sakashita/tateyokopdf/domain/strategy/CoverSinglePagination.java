package dev.sakashita.tateyokopdf.domain.strategy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import dev.sakashita.tateyokopdf.domain.model.SpreadHalf;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolates page 1 as a standalone cover on the reading-leading half, then pairs the rest from page
 * 2 ({@code [1], 2·3, 4·5, …}).
 */
public final class CoverSinglePagination implements PaginationStrategy {

  @Override
  public List<PagePairSpec> paginate(int totalPages) {
    Paginations.requirePages(totalPages);

    List<PagePairSpec> result = new ArrayList<>();
    result.add(new PagePairSpec.Single(0, SpreadHalf.LEADING));
    Paginations.pairFrom(result, 1, totalPages);
    return List.copyOf(result);
  }
}
