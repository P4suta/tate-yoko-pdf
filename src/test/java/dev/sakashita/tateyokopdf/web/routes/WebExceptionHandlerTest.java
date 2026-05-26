package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.observability.RequestTracingFilter;
import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WebExceptionHandlerTest {

  private static Javalin app() {
    TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);
    ViewRenderer renderer = new ViewRenderer(engine);
    WebExceptionHandler handler = new WebExceptionHandler(renderer);
    return Javalin.create(
        config -> {
          config.routes.before(RequestTracingFilter::before);
          config.routes.after(RequestTracingFilter::after);
          config.routes.get(
              "/boom-domain",
              ctx -> {
                throw SpreadException.of(ErrorKind.PDF_PASSWORD_PROTECTED);
              });
          config.routes.get(
              "/boom-runtime",
              ctx -> {
                throw new RuntimeException("oops");
              });
          config.routes.get(
              "/boom-iae",
              ctx -> {
                throw new IllegalArgumentException("bad arg");
              });
          config.routes.exception(SpreadException.class, handler::handleDomain);
          config.routes.exception(Exception.class, handler::handleUnknown);
          config.routes.error(404, handler::handleNotFound);
          config.routes.error(413, handler::handleTooLarge);
        });
  }

  private static String firstTraceIdHeader(io.javalin.testtools.Response resp) {
    List<String> values = resp.headers().get(RequestTracingFilter.HEADER_TRACE_ID);
    return (values == null || values.isEmpty()) ? "" : values.get(0);
  }

  @Test
  void domainExceptionRendersErrorPageWithKindAndTraceId() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var resp = client.get("/boom-domain");
          assertThat(resp.code()).isEqualTo(400);
          String trace = firstTraceIdHeader(resp);
          assertThat(trace).matches("^[0-9a-f]{32}$");
          String body = resp.body().string();
          assertThat(body).contains("PDF_PASSWORD_PROTECTED");
          assertThat(body).contains(trace);
        });
  }

  @Test
  void runtimeExceptionMappedToInternalAnd500() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var resp = client.get("/boom-runtime");
          assertThat(resp.code()).isEqualTo(500);
          assertThat(resp.body().string()).contains("INTERNAL");
        });
  }

  @Test
  void illegalArgumentExceptionMappedTo400InvalidParameter() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var resp = client.get("/boom-iae");
          assertThat(resp.code()).isEqualTo(400);
          assertThat(resp.body().string()).contains("INVALID_PARAMETER");
        });
  }

  @Test
  void unmatchedRouteRendersJobNotFoundPage() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var resp = client.get("/no-such-route");
          assertThat(resp.code()).isEqualTo(404);
          assertThat(resp.body().string()).contains("JOB_NOT_FOUND");
        });
  }
}
