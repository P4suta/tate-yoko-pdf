package dev.sakashita.tateyokopdf.observability;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

public final class TraceContext {

  public static final String MDC_TRACE_ID = "traceId";
  public static final String MDC_JOB_ID = "jobId";

  private TraceContext() {}

  public static String newTraceId() {
    UUID uuid = UUID.randomUUID();
    return digitsAsHex(uuid.getMostSignificantBits()) + digitsAsHex(uuid.getLeastSignificantBits());
  }

  public static void putTraceId(String traceId) {
    MDC.put(MDC_TRACE_ID, traceId);
  }

  public static void putJobId(@Nullable String jobId) {
    if (jobId == null) {
      MDC.remove(MDC_JOB_ID);
    } else {
      MDC.put(MDC_JOB_ID, jobId);
    }
  }

  public static @Nullable String currentTraceId() {
    return MDC.get(MDC_TRACE_ID);
  }

  public static void clear() {
    MDC.remove(MDC_TRACE_ID);
    MDC.remove(MDC_JOB_ID);
  }

  private static String digitsAsHex(long value) {
    return String.format("%016x", value);
  }
}
