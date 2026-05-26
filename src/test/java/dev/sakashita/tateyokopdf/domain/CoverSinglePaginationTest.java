package dev.sakashita.tateyokopdf.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import dev.sakashita.tateyokopdf.domain.strategy.CoverSinglePagination;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoverSinglePaginationTest {

  private final CoverSinglePagination strategy = new CoverSinglePagination();

  @Test
  void sixPages_coverThenPairsWithTrailingSingle() {
    List<PagePairSpec> result = strategy.paginate(6);

    assertThat(result)
        .containsExactly(
            new PagePairSpec.Single(0),
            new PagePairSpec.Pair(1, 2),
            new PagePairSpec.Pair(3, 4),
            new PagePairSpec.Single(5));
  }

  @Test
  void fivePages_coverThenPairs() {
    List<PagePairSpec> result = strategy.paginate(5);

    assertThat(result)
        .containsExactly(
            new PagePairSpec.Single(0), new PagePairSpec.Pair(1, 2), new PagePairSpec.Pair(3, 4));
  }

  @Test
  void singlePage_onlyCover() {
    List<PagePairSpec> result = strategy.paginate(1);

    assertThat(result).containsExactly(new PagePairSpec.Single(0));
  }

  @Test
  void twoPages_coverAndSingle() {
    List<PagePairSpec> result = strategy.paginate(2);

    assertThat(result).containsExactly(new PagePairSpec.Single(0), new PagePairSpec.Single(1));
  }

  @Test
  void zeroPages_throws() {
    assertThatThrownBy(() -> strategy.paginate(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
