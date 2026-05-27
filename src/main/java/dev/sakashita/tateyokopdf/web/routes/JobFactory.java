package dev.sakashita.tateyokopdf.web.routes;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Stages an uploaded PDF into a fresh temp work directory and registers a {@link Job} for it.
 * Isolated from {@link JobController} so the HTTP routing layer reads as routing, and the
 * stage-this-upload concern is independently testable.
 */
public final class JobFactory {

  private final JobRegistry registry;

  public JobFactory(JobRegistry registry) {
    this.registry = registry;
  }

  /**
   * Creates a temp work directory, copies the uploaded PDF into {@code input.pdf}, computes the
   * output path, and registers a Job in the registry. Throws {@link SpreadException} with {@link
   * ErrorKind#INTERNAL} if any filesystem step fails.
   */
  public Job stage(UploadedFile uploaded, String originalName, String traceId) {
    Path workDir;
    Path inputPath;
    Path outputPath;
    try {
      workDir = Files.createTempDirectory("tate-yoko-job-");
      inputPath = workDir.resolve("input.pdf");
      try (var in = uploaded.content()) {
        Files.copy(in, inputPath, StandardCopyOption.REPLACE_EXISTING);
      }
      String outputName = originalName.replaceFirst("(?i)\\.pdf$", "") + "_spread.pdf";
      outputPath = workDir.resolve(outputName);
    } catch (IOException e) {
      throw SpreadException.withDetail(ErrorKind.INTERNAL, "stage upload failed", e);
    }
    return registry.register(workDir, inputPath, outputPath, originalName, traceId);
  }
}
