package dev.sakashita.tateyokopdf.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StandardPaginationTest {

  private final StandardPagination strategy = new StandardPagination();

  @Test
  void singlePageProducesOneSingle() {
    assertThat(strategy.paginate(1)).containsExactly(new PagePairSpec.Single(0));
  }

  @Test
  void twoPagesProduceOnePair() {
    assertThat(strategy.paginate(2)).containsExactly(new PagePairSpec.Pair(0, 1));
  }

  @Test
  void evenPagesAllPaired() {
    List<PagePairSpec> result = strategy.paginate(4);
    assertThat(result).containsExactly(new PagePairSpec.Pair(0, 1), new PagePairSpec.Pair(2, 3));
  }

  @Test
  void oddPagesEndWithSingle() {
    List<PagePairSpec> result = strategy.paginate(5);
    assertThat(result)
        .containsExactly(
            new PagePairSpec.Pair(0, 1), new PagePairSpec.Pair(2, 3), new PagePairSpec.Single(4));
  }

  @Test
  void largeInputHandled() {
    List<PagePairSpec> result = strategy.paginate(1000);
    assertThat(result).hasSize(500);
    assertThat(result.get(0)).isEqualTo(new PagePairSpec.Pair(0, 1));
    assertThat(result.get(499)).isEqualTo(new PagePairSpec.Pair(998, 999));
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
    assertThatThrownBy(() -> strategy.paginate(-5)).isInstanceOf(SpreadException.class);
  }

  @Test
  void resultIsImmutable() {
    List<PagePairSpec> result = strategy.paginate(2);
    assertThatThrownBy(() -> result.add(new PagePairSpec.Single(99)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
