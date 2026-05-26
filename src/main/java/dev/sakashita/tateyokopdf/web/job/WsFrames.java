package dev.sakashita.tateyokopdf.web.job;

import dev.sakashita.tateyokopdf.port.exception.ErrorKind;

/** Centralised JSON wire-format helpers for the {@code /jobs/{id}/ws} stream. */
public final class WsFrames {

  private WsFrames() {}

  public static String progress(ProgressEvent event) {
    return switch (event) {
      case ProgressEvent.Started s ->
          "{\"type\":\"started\",\"total\":"
              + s.total()
              + ",\"traceId\":\""
              + escape(s.traceId())
              + "\"}";
      case ProgressEvent.Progress p ->
          "{\"type\":\"progress\",\"current\":"
              + p.current()
              + ",\"total\":"
              + p.total()
              + ",\"traceId\":\""
              + escape(p.traceId())
              + "\"}";
      case ProgressEvent.Completed c ->
          "{\"type\":\"completed\",\"traceId\":\"" + escape(c.traceId()) + "\"}";
      case ProgressEvent.Failed f ->
          "{\"type\":\"failed\",\"errorKind\":\""
              + f.errorKind().name()
              + "\",\"message\":\""
              + escape(f.message())
              + "\",\"traceId\":\""
              + escape(f.traceId())
              + "\"}";
    };
  }

  public static String error(ErrorKind kind, String userMessage, String traceId) {
    return "{\"type\":\"failed\",\"errorKind\":\""
        + kind.name()
        + "\",\"message\":\""
        + escape(userMessage)
        + "\",\"traceId\":\""
        + escape(traceId)
        + "\"}";
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
  }
}
