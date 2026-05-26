package dev.sakashita.tateyokopdf.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CoverSinglePaginationTest {

  private final CoverSinglePagination strategy = new CoverSinglePagination();

  @Test
  void singlePageProducesOnlyCover() {
    assertThat(strategy.paginate(1)).containsExactly(new PagePairSpec.Single(0));
  }

  @Test
  void twoPagesProduceCoverPlusSingle() {
    List<PagePairSpec> result = strategy.paginate(2);
    assertThat(result).containsExactly(new PagePairSpec.Single(0), new PagePairSpec.Single(1));
  }

  @Test
  void fivePagesProduceCoverThenPaired() {
    List<PagePairSpec> result = strategy.paginate(5);
    assertThat(result)
        .containsExactly(
            new PagePairSpec.Single(0), new PagePairSpec.Pair(1, 2), new PagePairSpec.Pair(3, 4));
  }

  @Test
  void sixPagesProduceCoverThenPairedThenSingle() {
    List<PagePairSpec> result = strategy.paginate(6);
    assertThat(result)
        .containsExactly(
            new PagePairSpec.Single(0),
            new PagePairSpec.Pair(1, 2),
            new PagePairSpec.Pair(3, 4),
            new PagePairSpec.Single(5));
  }

  @Test
  void coverIsAlwaysFirst() {
    for (int n : new int[] {1, 2, 3, 4, 5, 6, 10, 99}) {
      assertThat(strategy.paginate(n).get(0)).isEqualTo(new PagePairSpec.Single(0));
    }
  }

  @Test
  void zeroRejected() {
    assertThatThrownBy(() -> strategy.paginate(0))
        .isInstanceOfSatisfying(
            SpreadException.class,
            ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_INVALID_PAGE));
  }

  @Test
  void negativeRejected() {
    assertThatThrownBy(() -> strategy.paginate(-1)).isInstanceOf(SpreadException.class);
  }
}
