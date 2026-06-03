package dev.sakashita.tateyokopdf.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.domain.model.FirstPageMode;
import dev.sakashita.tateyokopdf.domain.strategy.CoverSinglePagination;
import dev.sakashita.tateyokopdf.domain.strategy.LeadingBlankPagination;
import dev.sakashita.tateyokopdf.domain.strategy.StandardPagination;
import org.junit.jupiter.api.Test;

final class PaginationStrategyFactoryTest {

  @Test
  void standardModeSelectsStandardPagination() {
    assertThat(PaginationStrategyFactory.from(FirstPageMode.STANDARD))
        .isInstanceOf(StandardPagination.class);
  }

  @Test
  void coverModeSelectsCoverSinglePagination() {
    assertThat(PaginationStrategyFactory.from(FirstPageMode.COVER))
        .isInstanceOf(CoverSinglePagination.class);
  }

  @Test
  void leadingBlankModeSelectsLeadingBlankPagination() {
    assertThat(PaginationStrategyFactory.from(FirstPageMode.LEADING_BLANK))
        .isInstanceOf(LeadingBlankPagination.class);
  }
}
