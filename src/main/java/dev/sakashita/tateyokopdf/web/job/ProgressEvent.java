package dev.sakashita.tateyokopdf.web.job;

import dev.sakashita.tateyokopdf.port.exception.ErrorKind;

public sealed interface ProgressEvent {

  /** Trace ID of the originating request; carried into every WS frame for support correlation. */
  String traceId();

  record Started(int total, String traceId) implements ProgressEvent {}

  record Progress(int current, int total, String traceId) implements ProgressEvent {}

  record Completed(String traceId) implements ProgressEvent {}

  record Failed(ErrorKind errorKind, String message, String traceId) implements ProgressEvent {}
}
