package dev.sakashita.tateyokopdf.web.job;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;

/**
 * WebSocket frame contract for {@code /ws/jobs/{id}}. The sealed hierarchy is also the JSON wire
 * shape — Jackson picks a discriminator-tagged subtype via {@code @JsonTypeInfo}, so the frontend
 * {@code types.ts} can stay a thin manual mirror until a codegen path is added.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ProgressEvent.Started.class, name = "started"),
  @JsonSubTypes.Type(value = ProgressEvent.Progress.class, name = "progress"),
  @JsonSubTypes.Type(value = ProgressEvent.Completed.class, name = "completed"),
  @JsonSubTypes.Type(value = ProgressEvent.Failed.class, name = "failed"),
})
public sealed interface ProgressEvent {

  /** Trace ID of the originating request; carried into every WS frame for support correlation. */
  String traceId();

  record Started(int total, String traceId) implements ProgressEvent {}

  record Progress(int current, int total, String traceId) implements ProgressEvent {}

  record Completed(String traceId) implements ProgressEvent {}

  record Failed(ErrorKind errorKind, String message, String traceId) implements ProgressEvent {}
}
