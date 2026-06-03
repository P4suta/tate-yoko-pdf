package dev.sakashita.tateyokopdf.domain.strategy;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.Validators;
import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import java.util.List;

/**
 * Shared pagination primitives reused across {@link PaginationStrategy} implementations, so the
 * common "pair adjacent pages" loop lives in exactly one place.
 */
final class Paginations {

  private Paginations() {}

  /** Guards the page count every strategy requires before paginating. */
  static void requirePages(int totalPages) {
    Validators.require(totalPages > 0, ErrorKind.PDF_INVALID_PAGE, "totalPages=" + totalPages);
  }

  /**
   * Appends spreads covering the page range {@code [start, totalPages)} by pairing adjacent pages
   * ({@code start·start+1}, {@code start+2·start+3}, …). A lone trailing page (odd-length range)
   * becomes a leading {@link PagePairSpec.Single}.
   */
  static void pairFrom(List<PagePairSpec> out, int start, int totalPages) {
    for (int i = start; i < totalPages; i += 2) {
      out.add(i + 1 < totalPages ? new PagePairSpec.Pair(i, i + 1) : new PagePairSpec.Single(i));
    }
  }
}
