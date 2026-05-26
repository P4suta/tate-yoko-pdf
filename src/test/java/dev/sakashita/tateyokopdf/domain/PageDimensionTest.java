package dev.sakashita.tateyokopdf.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sakashita.tateyokopdf.domain.model.PageDimension;
import org.junit.jupiter.api.Test;

class PageDimensionTest {

  @Test
  void validDimensions() {
    var dim = new PageDimension(595.28f, 841.89f);

    assertThat(dim.widthPt()).isEqualTo(595.28f);
    assertThat(dim.heightPt()).isEqualTo(841.89f);
  }

  @Test
  void zeroWidth_throws() {
    assertThatThrownBy(() -> new PageDimension(0, 100))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeHeight_throws() {
    assertThatThrownBy(() -> new PageDimension(100, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void max_takesLargerOfEachDimension() {
    var a = new PageDimension(400, 800);
    var b = new PageDimension(500, 700);

    PageDimension result = PageDimension.max(a, b);

    assertThat(result).isEqualTo(new PageDimension(500, 800));
  }
}
