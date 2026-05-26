package dev.sakashita.tateyokopdf.web.routes;

import dev.sakashita.tateyokopdf.observability.ExceptionMapper;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.javalin.http.Context;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ViewRenderer {

  private final TemplateEngine engine;

  public ViewRenderer(TemplateEngine engine) {
    this.engine = engine;
  }

  public void renderHtml(Context ctx, String template, @Nullable Object model) {
    var out = new StringOutput();
    engine.render(template, model, out);
    ctx.html(out.toString());
  }

  public void renderError(Context ctx, ExceptionMapper.Mapping mapping, String traceId) {
    var out = new StringOutput();
    engine.render(
        "error.jte",
        Map.of(
            "kind", mapping.kind().name(),
            "message", mapping.userMessage(),
            "traceId", traceId),
        out);
    ctx.status(mapping.httpStatus()).html(out.toString());
  }
}
