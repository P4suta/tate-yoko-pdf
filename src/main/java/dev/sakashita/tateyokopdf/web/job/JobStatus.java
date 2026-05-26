package dev.sakashita.tateyokopdf.web.job;

public sealed interface JobStatus {
  record Pending() implements JobStatus {}

  record Running(int current, int total) implements JobStatus {}

  record Completed() implements JobStatus {}

  record Failed(String message) implements JobStatus {}
}
