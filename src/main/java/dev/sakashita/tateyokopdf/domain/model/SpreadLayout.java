package dev.sakashita.tateyokopdf.domain.model;

import java.util.Optional;

public record SpreadLayout(
    SpreadSpec spec,
    LayoutPosition firstPosition,
    Optional<LayoutPosition> secondPosition
) {
    public SpreadLayout {
        if (spec == null || firstPosition == null || secondPosition == null) {
            throw new IllegalArgumentException("All fields must be non-null");
        }
    }
}
