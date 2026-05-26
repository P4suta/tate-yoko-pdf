package dev.sakashita.tateyokopdf.web.routes;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.javalin.http.Context;

public final class PageController {

  private final TemplateEngine engine;

  public PageController(TemplateEngine engine) {
    this.engine = engine;
  }

  public void index(Context ctx) {
    var out = new StringOutput();
    engine.render("index.jte", null, out);
    ctx.html(out.toString());
  }
}
