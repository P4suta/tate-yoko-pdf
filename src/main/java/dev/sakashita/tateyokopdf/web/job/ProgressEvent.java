package dev.sakashita.tateyokopdf.web.job;

public sealed interface ProgressEvent {
  record Started(int total) implements ProgressEvent {}

  record Progress(int current, int total) implements ProgressEvent {}

  record Completed() implements ProgressEvent {}

  record Failed(String message) implements ProgressEvent {}
}
