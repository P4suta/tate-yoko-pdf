package dev.sakashita.tateyokopdf.web.routes;

import dev.sakashita.tateyokopdf.application.SpreadOptions;
import dev.sakashita.tateyokopdf.application.SpreadService;
import dev.sakashita.tateyokopdf.domain.model.ReadingDirection;
import dev.sakashita.tateyokopdf.domain.service.SpreadLayoutCalculator;
import dev.sakashita.tateyokopdf.domain.strategy.CoverSinglePagination;
import dev.sakashita.tateyokopdf.domain.strategy.PaginationStrategy;
import dev.sakashita.tateyokopdf.domain.strategy.StandardPagination;
import dev.sakashita.tateyokopdf.infrastructure.pdfbox.PdfBoxDocumentFactory;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.job.NoOpProgressListener;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobController {

  private static final Logger log = LoggerFactory.getLogger(JobController.class);

  private final JobRegistry registry;
  private final TemplateEngine engine;

  public JobController(JobRegistry registry, TemplateEngine engine) {
    this.registry = registry;
    this.engine = engine;
  }

  public void submit(Context ctx) {
    UploadedFile uploaded = ctx.uploadedFile("pdf");
    if (uploaded == null || uploaded.size() == 0) {
      ctx.status(HttpStatus.BAD_REQUEST).result("pdf file is required");
      return;
    }

    String dirParam = ctx.formParamAsClass("direction", String.class).getOrDefault("RTL");
    ReadingDirection direction;
    try {
      direction = ReadingDirection.valueOf(dirParam.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      ctx.status(HttpStatus.BAD_REQUEST).result("direction must be RTL or LTR");
      return;
    }
    boolean coverSingle = ctx.formParam("coverSingle") != null;

    Path workDir;
    Path inputPath;
    Path outputPath;
    String originalName =
        (uploaded.filename() != null && !uploaded.filename().isBlank())
            ? uploaded.filename()
            : "input.pdf";
    try {
      workDir = Files.createTempDirectory("tate-yoko-job-");
      inputPath = workDir.resolve("input.pdf");
      try (var in = uploaded.content()) {
        Files.copy(in, inputPath, StandardCopyOption.REPLACE_EXISTING);
      }
      String outputName = originalName.replaceFirst("(?i)\\.pdf$", "") + "_spread.pdf";
      outputPath = workDir.resolve(outputName);
    } catch (IOException e) {
      log.error("Failed to stage upload", e);
      ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to stage upload");
      return;
    }

    SpreadOptions options = new SpreadOptions(inputPath, outputPath, direction, coverSingle);
    PaginationStrategy strategy =
        coverSingle ? new CoverSinglePagination() : new StandardPagination();
    var service =
        new SpreadService(
            new PdfBoxDocumentFactory(),
            new SpreadLayoutCalculator(),
            strategy,
            new NoOpProgressListener());

    try {
      service.execute(options);
    } catch (SpreadException e) {
      log.warn("Spread failed for {}: {}", originalName, e.getMessage());
      cleanupQuietly(workDir);
      renderError(ctx, Objects.requireNonNullElse(e.getMessage(), "原因不明のエラー"));
      return;
    } catch (RuntimeException e) {
      log.error("Unexpected error during spread for {}", originalName, e);
      cleanupQuietly(workDir);
      renderError(
          ctx,
          "予期しないエラーが発生しました: "
              + Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName()));
      return;
    }

    Job job = registry.register(workDir, inputPath, outputPath, originalName);
    ctx.redirect("/jobs/" + job.id() + "/result");
  }

  public void showResult(Context ctx) {
    UUID id;
    try {
      id = UUID.fromString(ctx.pathParam("id"));
    } catch (IllegalArgumentException e) {
      ctx.status(HttpStatus.NOT_FOUND).result("Job not found");
      return;
    }
    Job job = registry.find(id).orElse(null);
    if (job == null) {
      ctx.status(HttpStatus.NOT_FOUND).result("Job not found");
      return;
    }
    var out = new StringOutput();
    engine.render("result.jte", Map.of("job", job), out);
    ctx.html(out.toString());
  }

  public void download(Context ctx) {
    UUID id;
    try {
      id = UUID.fromString(ctx.pathParam("id"));
    } catch (IllegalArgumentException e) {
      ctx.status(HttpStatus.NOT_FOUND).result("Job not found");
      return;
    }
    Job job = registry.find(id).orElse(null);
    if (job == null) {
      ctx.status(HttpStatus.NOT_FOUND).result("Job not found");
      return;
    }
    Path output = job.outputPath();
    if (!Files.isRegularFile(output)) {
      ctx.status(HttpStatus.GONE).result("Output file no longer available");
      return;
    }

    long size;
    InputStream stream;
    try {
      size = Files.size(output);
      stream = Files.newInputStream(output);
    } catch (IOException e) {
      log.error("Failed to open output for job {}", id, e);
      ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to read output");
      return;
    }

    ctx.contentType("application/pdf");
    ctx.header(
        "Content-Disposition",
        "attachment; filename*=UTF-8''" + urlEncode(downloadName(job.originalName())));
    ctx.header("Content-Length", Long.toString(size));
    ctx.result(stream);
  }

  private static String downloadName(String originalName) {
    String base = originalName.replaceFirst("(?i)\\.pdf$", "");
    return base + "_spread.pdf";
  }

  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private void renderError(Context ctx, String message) {
    var out = new StringOutput();
    engine.render("error.jte", Map.of("message", message), out);
    ctx.status(HttpStatus.UNPROCESSABLE_CONTENT).html(out.toString());
  }

  private static void cleanupQuietly(Path workDir) {
    try (var paths = Files.walk(workDir)) {
      paths
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException e) {
                  log.debug("Failed to delete {}: {}", p, e.getMessage());
                }
              });
    } catch (IOException e) {
      log.debug("Failed to walk {}: {}", workDir, e.getMessage());
    }
  }
}
