package dev.sakashita.tateyokopdf.domain.model;

import java.util.Optional;

/**
 * The computed geometry of one spread: the frame size and where each page sits in it.
 *
 * @param spec the spread frame size
 * @param firstPosition where the first (or lone) page sits
 * @param secondPosition where the second page sits, or empty for a single-page spread
 */
public record SpreadLayout(
    SpreadSpec spec, LayoutPosition firstPosition, Optional<LayoutPosition> secondPosition) {
  public SpreadLayout {
    if (spec == null || firstPosition == null || secondPosition == null) {
      throw new IllegalArgumentException("All fields must be non-null");
    }
  }
}
