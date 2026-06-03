package dev.sakashita.tateyokopdf.property;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.domain.model.FirstPageMode;
import dev.sakashita.tateyokopdf.domain.model.OpeningSide;
import dev.sakashita.tateyokopdf.domain.model.ReadingDirection;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

final class FirstPageModeProperties {

  @Property
  void leadingSideIsStandardOtherwiseLeadingBlank(
      @ForAll OpeningSide side, @ForAll ReadingDirection direction) {
    FirstPageMode mode = FirstPageMode.fromSide(side, direction);
    if (side == OpeningSide.leadingFor(direction)) {
      assertThat(mode).isEqualTo(FirstPageMode.STANDARD);
    } else {
      assertThat(mode).isEqualTo(FirstPageMode.LEADING_BLANK);
    }
  }

  @Property
  void fromSideNeverYieldsCover(@ForAll OpeningSide side, @ForAll ReadingDirection direction) {
    // COVER is requested explicitly (--first-page cover), never derived from a side.
    assertThat(FirstPageMode.fromSide(side, direction)).isNotEqualTo(FirstPageMode.COVER);
  }
}
