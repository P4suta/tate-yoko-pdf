package dev.sakashita.tateyokopdf.web.job;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public final class Job {

  private final UUID id;
  private final Path workDir;
  private final Path inputPath;
  private final Path outputPath;
  private final String originalName;
  private final Instant createdAt;
  private final String traceId;
  private volatile JobStatus status;

  public Job(
      UUID id,
      Path workDir,
      Path inputPath,
      Path outputPath,
      String originalName,
      Instant createdAt) {
    this(id, workDir, inputPath, outputPath, originalName, createdAt, "-");
  }

  public Job(
      UUID id,
      Path workDir,
      Path inputPath,
      Path outputPath,
      String originalName,
      Instant createdAt,
      String traceId) {
    this.id = id;
    this.workDir = workDir;
    this.inputPath = inputPath;
    this.outputPath = outputPath;
    this.originalName = originalName;
    this.createdAt = createdAt;
    this.traceId = traceId;
    this.status = new JobStatus.Pending();
  }

  public UUID id() {
    return id;
  }

  public Path workDir() {
    return workDir;
  }

  public Path inputPath() {
    return inputPath;
  }

  public Path outputPath() {
    return outputPath;
  }

  public String originalName() {
    return originalName;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String traceId() {
    return traceId;
  }

  public JobStatus status() {
    return status;
  }

  public void setStatus(JobStatus status) {
    this.status = status;
  }
}
