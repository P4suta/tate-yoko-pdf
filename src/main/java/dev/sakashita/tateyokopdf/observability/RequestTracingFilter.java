package dev.sakashita.tateyokopdf.observability;

import io.javalin.http.Context;

public final class RequestTracingFilter {

  public static final String ATTR_TRACE_ID = "traceId";
  public static final String HEADER_TRACE_ID = "X-Trace-Id";

  private RequestTracingFilter() {}

  public static void before(Context ctx) {
    String traceId = TraceContext.newTraceId();
    ctx.attribute(ATTR_TRACE_ID, traceId);
    ctx.header(HEADER_TRACE_ID, traceId);
    TraceContext.putTraceId(traceId);
  }

  public static void after(Context ctx) {
    TraceContext.clear();
  }
}
