package dev.sakashita.tateyokopdf.domain.strategy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import dev.sakashita.tateyokopdf.domain.model.SpreadHalf;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens with page 1 on the trailing half — across from an implied leading blank — then pairs the
 * rest from page 2 ({@code [▢|1], 2·3, 4·5, …}). It is the mirror of {@link CoverSinglePagination}:
 * the same grouping, but page 1 sits on the opposite side. Used for "first page starts on the
 * non-leading side".
 */
public final class LeadingBlankPagination implements PaginationStrategy {

  @Override
  public List<PagePairSpec> paginate(int totalPages) {
    Paginations.requirePages(totalPages);

    List<PagePairSpec> result = new ArrayList<>();
    result.add(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
    Paginations.pairFrom(result, 1, totalPages);
    return List.copyOf(result);
  }
}
