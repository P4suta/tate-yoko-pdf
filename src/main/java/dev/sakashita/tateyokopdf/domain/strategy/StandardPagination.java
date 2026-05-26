package dev.sakashita.tateyokopdf.domain.strategy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import java.util.ArrayList;
import java.util.List;

public final class StandardPagination implements PaginationStrategy {

  @Override
  public List<PagePairSpec> paginate(int totalPages) {
    if (totalPages <= 0) {
      throw new IllegalArgumentException("totalPages must be positive: " + totalPages);
    }

    List<PagePairSpec> result = new ArrayList<>();

    for (int i = 0; i < totalPages; i += 2) {
      if (i + 1 < totalPages) {
        result.add(new PagePairSpec.Pair(i, i + 1));
      } else {
        result.add(new PagePairSpec.Single(i));
      }
    }

    return List.copyOf(result);
  }
}
