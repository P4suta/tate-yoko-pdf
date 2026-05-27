package dev.sakashita.tateyokopdf.web.job;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable job identity + filesystem coordinates. Runtime state (started / running / completed /
 * failed) lives entirely on {@link WebProgressListener#history()} — the listener's event stream is
 * the single source of truth for "where is this job now."
 */
public final class Job {

  private final UUID id;
  private final Path workDir;
  private final Path inputPath;
  private final Path outputPath;
  private final String originalName;
  private final Instant createdAt;
  private final String traceId;

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
}
