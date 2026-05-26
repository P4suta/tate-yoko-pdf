package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RequestTracingFilterTest {

  private static Javalin app() {
    return Javalin.create(
        config -> {
          config.routes.before(RequestTracingFilter::before);
          config.routes.after(RequestTracingFilter::after);
          config.routes.get(
              "/echo",
              ctx -> {
                String trace = ctx.attribute(RequestTracingFilter.ATTR_TRACE_ID);
                String mdc = TraceContext.currentTraceId();
                ctx.result((trace == null ? "?" : trace) + "|" + (mdc == null ? "?" : mdc));
              });
        });
  }

  @Test
  void beforeAttachesTraceIdAttributeAndHeaderAndMdc() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          var resp = client.get("/echo");
          assertThat(resp.code()).isEqualTo(200);
          List<String> headerValues = resp.headers().get(RequestTracingFilter.HEADER_TRACE_ID);
          assertThat(headerValues).isNotNull().isNotEmpty();
          String header = headerValues == null ? "" : headerValues.get(0);
          assertThat(header).matches("^[0-9a-f]{32}$");
          String body = resp.body().string();
          assertThat(body).startsWith(header + "|");
          assertThat(body.substring(33)).isEqualTo(header);
        });
  }

  @Test
  void afterClearsMdcOnTheCallingThread() {
    JavalinTest.test(
        app(),
        (server, client) -> {
          client.get("/echo");
          // The Jetty thread that ran our handler will clear its own MDC in the after-hook.
          // The test (main) thread never had a traceId, so it remains null here.
          assertThat(TraceContext.currentTraceId()).isNull();
        });
  }
}
