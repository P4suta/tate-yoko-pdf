package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.observability.ExceptionMapper;
import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

final class ViewRendererTest {

  private static Javalin app() {
    TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);
    ViewRenderer renderer = new ViewRenderer(engine);
    return Javalin.create(
        config -> {
          config.routes.get(
              "/err",
              ctx ->
                  renderer.renderError(
                      ctx,
                      ExceptionMapper.map(SpreadException.of(ErrorKind.PDF_CORRUPTED)),
                      "trc-001"));
          config.routes.get("/idx", ctx -> renderer.renderHtml(ctx, "index.jte", null));
        });
  }

  @Test
  void renderErrorEmbedsTraceIdAndKindAndUserMessage() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var resp = client.get("/err");
          assertThat(resp.code()).isEqualTo(400);
          String body = resp.body().string();
          assertThat(body).contains("trc-001");
          assertThat(body).contains("PDF_CORRUPTED");
          assertThat(body).contains(ErrorKind.PDF_CORRUPTED.defaultUserMessage());
        });
  }

  @Test
  void renderHtmlRendersTemplateWithoutModel() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var resp = client.get("/idx");
          assertThat(resp.code()).isEqualTo(200);
          String body = resp.body().string();
          assertThat(body).contains("<form");
        });
  }
}
