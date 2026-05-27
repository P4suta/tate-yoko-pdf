package dev.sakashita.tateyokopdf.web.routes;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.observability.ExceptionMapper;
import dev.sakashita.tateyokopdf.observability.TraceContext;
import dev.sakashita.tateyokopdf.web.observability.RequestTracingFilter;
import io.javalin.http.Context;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public final class WebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);
  private static final String ATTR_HANDLED = "tate-yoko.errorHandled";

  public void handleDomain(Exception e, Context ctx) {
    render(e, ctx);
  }

  public void handleUnknown(Exception e, Context ctx) {
    render(e, ctx);
  }

  public void handleFatal(Throwable t, Context ctx) {
    render(t, ctx);
  }

  public void handleNotFound(Context ctx) {
    if (alreadyHandled(ctx)) {
      return;
    }
    render(SpreadException.of(ErrorKind.JOB_NOT_FOUND), ctx);
  }

  public void handleTooLarge(Context ctx) {
    if (alreadyHandled(ctx)) {
      return;
    }
    render(SpreadException.of(ErrorKind.PDF_TOO_LARGE), ctx);
  }

  private void render(Throwable t, Context ctx) {
    ExceptionMapper.Mapping mapping = ExceptionMapper.map(t);
    String traceId = traceIdOf(ctx);
    logError(mapping, traceId, t);
    ctx.status(mapping.httpStatus());
    ctx.contentType("application/json");
    ctx.result(
        "{\"kind\":\""
            + mapping.kind().name()
            + "\",\"message\":\""
            + escape(mapping.userMessage())
            + "\",\"traceId\":\""
            + escape(traceId)
            + "\"}");
    ctx.attribute(ATTR_HANDLED, true);
  }

  private static boolean alreadyHandled(Context ctx) {
    Boolean v = ctx.attribute(ATTR_HANDLED);
    return v != null && v;
  }

  private static String traceIdOf(Context ctx) {
    String traceId = ctx.attribute(RequestTracingFilter.ATTR_TRACE_ID);
    if (traceId != null) {
      return traceId;
    }
    String current = TraceContext.currentTraceId();
    return current != null ? current : "-";
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
  }

  private static void logError(
      ExceptionMapper.Mapping mapping, String traceId, @Nullable Throwable t) {
    if (mapping.logLevel() == Level.ERROR) {
      log.error(
          "[{}] {} (traceId={}, detail={})",
          mapping.kind(),
          mapping.userMessage(),
          traceId,
          mapping.technicalDetail(),
          t);
    } else if (mapping.logLevel() == Level.WARN) {
      log.warn(
          "[{}] {} (traceId={}, detail={})",
          mapping.kind(),
          mapping.userMessage(),
          traceId,
          mapping.technicalDetail());
    } else {
      log.info(
          "[{}] {} (traceId={}, detail={})",
          mapping.kind(),
          mapping.userMessage(),
          traceId,
          mapping.technicalDetail());
    }
  }
}
