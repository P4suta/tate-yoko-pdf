package dev.sakashita.tateyokopdf.port;

import dev.sakashita.tateyokopdf.domain.model.LayoutPosition;

public record PagePlacement(PageContent content, LayoutPosition position) {
  public PagePlacement {
    if (content == null || position == null) {
      throw new IllegalArgumentException("content and position must not be null");
    }
  }
}
