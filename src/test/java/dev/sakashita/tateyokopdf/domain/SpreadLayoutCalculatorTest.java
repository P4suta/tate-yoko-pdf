package dev.sakashita.tateyokopdf.domain;

import dev.sakashita.tateyokopdf.domain.model.*;
import dev.sakashita.tateyokopdf.domain.service.SpreadLayoutCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadLayoutCalculatorTest {

    private final SpreadLayoutCalculator calculator = new SpreadLayoutCalculator();

    @Test
    void rtl_pair_placesFirstPageOnRightHalf() {
        var dim = new PageDimension(500, 800);

        SpreadLayout layout = calculator.calculate(ReadingDirection.RTL, dim, dim);

        assertThat(layout.spec()).isEqualTo(new SpreadSpec(1000, 800));
        assertThat(layout.firstPosition().offsetXPt()).isEqualTo(500f);
        assertThat(layout.secondPosition().orElseThrow().offsetXPt()).isEqualTo(0f);
    }

    @Test
    void ltr_pair_placesFirstPageOnLeftHalf() {
        var dim = new PageDimension(500, 800);

        SpreadLayout layout = calculator.calculate(ReadingDirection.LTR, dim, dim);

        assertThat(layout.spec()).isEqualTo(new SpreadSpec(1000, 800));
        assertThat(layout.firstPosition().offsetXPt()).isEqualTo(0f);
        assertThat(layout.secondPosition().orElseThrow().offsetXPt()).isEqualTo(500f);
    }

    @Test
    void unequalSizes_centersWithinHalf() {
        var small = new PageDimension(400, 700);
        var large = new PageDimension(500, 800);

        SpreadLayout layout = calculator.calculate(ReadingDirection.RTL, small, large);

        assertThat(layout.spec()).isEqualTo(new SpreadSpec(1000, 800));
        assertThat(layout.firstPosition().offsetXPt()).isEqualTo(550f);
        assertThat(layout.firstPosition().offsetYPt()).isEqualTo(50f);
    }

    @Test
    void singlePage_hasEmptySecondPosition() {
        var dim = new PageDimension(500, 800);

        SpreadLayout layout = calculator.calculate(ReadingDirection.RTL, dim, null);

        assertThat(layout.secondPosition()).isEmpty();
        assertThat(layout.spec().widthPt()).isEqualTo(1000f);
    }
}
