package dev.sakashita.tateyokopdf.domain.strategy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import java.util.ArrayList;
import java.util.List;

/** Pairs pages from the first: {@code 1·2, 3·4, …}; an odd final page becomes a leading single. */
public final class StandardPagination implements PaginationStrategy {

  @Override
  public List<PagePairSpec> paginate(int totalPages) {
    Paginations.requirePages(totalPages);

    List<PagePairSpec> result = new ArrayList<>();
    Paginations.pairFrom(result, 0, totalPages);
    return List.copyOf(result);
  }
}
