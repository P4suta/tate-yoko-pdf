package dev.sakashita.tateyokopdf.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import dev.sakashita.tateyokopdf.domain.model.SpreadHalf;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LeadingBlankPaginationTest {

  private final LeadingBlankPagination strategy = new LeadingBlankPagination();

  @Test
  void singlePageProducesTrailingSingle() {
    assertThat(strategy.paginate(1))
        .containsExactly(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
  }

  @Test
  void twoPagesProduceTrailingThenLeadingSingle() {
    assertThat(strategy.paginate(2))
        .containsExactly(
            new PagePairSpec.Single(0, SpreadHalf.TRAILING),
            new PagePairSpec.Single(1, SpreadHalf.LEADING));
  }

  @Test
  void fivePagesLeadWithBlankThenPaired() {
    assertThat(strategy.paginate(5))
        .containsExactly(
            new PagePairSpec.Single(0, SpreadHalf.TRAILING),
            new PagePairSpec.Pair(1, 2),
            new PagePairSpec.Pair(3, 4));
  }

  @Test
  void sixPagesLeadWithBlankThenPairedThenTrailingLeftover() {
    assertThat(strategy.paginate(6))
        .containsExactly(
            new PagePairSpec.Single(0, SpreadHalf.TRAILING),
            new PagePairSpec.Pair(1, 2),
            new PagePairSpec.Pair(3, 4),
            new PagePairSpec.Single(5, SpreadHalf.LEADING));
  }

  @Test
  void firstPageIsAlwaysATrailingSingle() {
    for (int n : new int[] {1, 2, 3, 4, 5, 6, 10, 99}) {
      assertThat(strategy.paginate(n).get(0))
          .isEqualTo(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
    }
  }

  @Test
  void mirrorsCoverSingleExceptForPageZerosHalf() {
    // The two offset strategies share their grouping; only page 0's half differs.
    List<PagePairSpec> leadingBlank = strategy.paginate(7);
    List<PagePairSpec> cover = new CoverSinglePagination().paginate(7);
    assertThat(leadingBlank.subList(1, leadingBlank.size()))
        .isEqualTo(cover.subList(1, cover.size()));
    assertThat(leadingBlank.get(0)).isEqualTo(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
    assertThat(cover.get(0)).isEqualTo(new PagePairSpec.Single(0, SpreadHalf.LEADING));
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
