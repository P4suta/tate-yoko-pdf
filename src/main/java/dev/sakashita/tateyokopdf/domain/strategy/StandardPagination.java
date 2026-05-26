package dev.sakashita.tateyokopdf.domain.strategy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.Validators;
import java.util.ArrayList;
import java.util.List;

public final class StandardPagination implements PaginationStrategy {

  @Override
  public List<PagePairSpec> paginate(int totalPages) {
    Validators.require(totalPages > 0, ErrorKind.PDF_INVALID_PAGE, "totalPages=" + totalPages);

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
