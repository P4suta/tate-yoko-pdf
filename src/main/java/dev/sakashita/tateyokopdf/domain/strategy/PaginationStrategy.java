package dev.sakashita.tateyokopdf.domain.strategy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import java.util.List;

public sealed interface PaginationStrategy
    permits StandardPagination, CoverSinglePagination {

    List<PagePairSpec> paginate(int totalPages);
}
