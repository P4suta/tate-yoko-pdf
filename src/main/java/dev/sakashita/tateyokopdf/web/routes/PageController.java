package dev.sakashita.tateyokopdf.web.routes;

import io.javalin.http.Context;

public final class PageController {

  private final ViewRenderer renderer;

  public PageController(ViewRenderer renderer) {
    this.renderer = renderer;
  }

  public void index(Context ctx) {
    renderer.renderHtml(ctx, "index.jte", null);
  }
}
