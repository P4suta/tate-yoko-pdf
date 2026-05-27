package dev.sakashita.tateyokopdf.web.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;

/**
 * Single ObjectMapper for every WebSocket frame the web layer emits. Centralising the configuration
 * means the wire format is defined exactly once and the {@code @JsonTypeInfo} discriminator on
 * {@link ProgressEvent} is the sole source of truth — no hand-written JSON, no string escaping
 * logic in N places.
 */
public final class JobJsonMapping {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JobJsonMapping() {}

  public static String toJson(ProgressEvent event) {
    try {
      return MAPPER.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      // ProgressEvent only contains primitive / String / enum fields, so this branch is
      // unreachable in practice. Convert defensively rather than declaring a checked throw.
      throw new IllegalStateException("ProgressEvent serialization failed", e);
    }
  }

  /**
   * Convenience for the path where the web layer needs to emit a Failed frame without an upstream
   * ProgressEvent (e.g. unknown job ID at WS connect time).
   */
  public static String failedFrame(ErrorKind kind, String userMessage, String traceId) {
    return toJson(new ProgressEvent.Failed(kind, userMessage, traceId));
  }
}
