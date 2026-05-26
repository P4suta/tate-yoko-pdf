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
import dev.sakashita.tateyokopdf.web.job.JobStatus;
import dev.sakashita.tateyokopdf.web.job.ProgressEvent;
import dev.sakashita.tateyokopdf.web.job.WebProgressListener;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import io.javalin.websocket.WsConnectContext;
import java.io.FilterInputStream;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobController {

  private static final Logger log = LoggerFactory.getLogger(JobController.class);

  private final JobRegistry registry;
  private final TemplateEngine engine;
  private final ExecutorService executor;

  public JobController(JobRegistry registry, TemplateEngine engine, ExecutorService executor) {
    this.registry = registry;
    this.engine = engine;
    this.executor = executor;
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

    Job job = registry.register(workDir, inputPath, outputPath, originalName);
    WebProgressListener listener =
        registry
            .listener(job.id())
            .orElseThrow(() -> new IllegalStateException("listener missing after register"));

    SpreadOptions options = new SpreadOptions(inputPath, outputPath, direction, coverSingle);
    PaginationStrategy strategy =
        coverSingle ? new CoverSinglePagination() : new StandardPagination();
    SpreadService service =
        new SpreadService(
            new PdfBoxDocumentFactory(), new SpreadLayoutCalculator(), strategy, listener);

    var ignored =
        executor.submit(
            () -> {
              try {
                service.execute(options);
              } catch (SpreadException e) {
                log.warn("Spread failed for {}: {}", originalName, e.getMessage());
                listener.fail(Objects.requireNonNullElse(e.getMessage(), "原因不明のエラー"));
              } catch (RuntimeException e) {
                log.error("Unexpected error during spread for {}", originalName, e);
                listener.fail(
                    "予期しないエラーが発生しました: "
                        + Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName()));
              }
            });

    ctx.redirect("/jobs/" + job.id() + "/progress");
  }

  public void showProgress(Context ctx) {
    Job job = lookupOrError(ctx);
    if (job == null) {
      return;
    }
    var out = new StringOutput();
    engine.render("progress.jte", Map.of("job", job), out);
    ctx.html(out.toString());
  }

  public void showResult(Context ctx) {
    Job job = lookupOrError(ctx);
    if (job == null) {
      return;
    }
    if (!(job.status() instanceof JobStatus.Completed)) {
      ctx.redirect("/jobs/" + job.id() + "/progress");
      return;
    }
    var out = new StringOutput();
    engine.render("result.jte", Map.of("job", job), out);
    ctx.html(out.toString());
  }

  public void onProgressWs(WsConnectContext ctx) {
    UUID id;
    try {
      id = UUID.fromString(ctx.pathParam("id"));
    } catch (IllegalArgumentException e) {
      ctx.closeSession(1008, "Job not found");
      return;
    }
    WebProgressListener listener = registry.listener(id).orElse(null);
    if (listener == null) {
      ctx.closeSession(1008, "Job not found");
      return;
    }
    BlockingQueue<ProgressEvent> queue = listener.subscribe();
    Thread pump =
        new Thread(
            () -> {
              try {
                while (ctx.session.isOpen()) {
                  ProgressEvent event = queue.poll(15, TimeUnit.SECONDS);
                  if (event == null) {
                    continue;
                  }
                  ctx.send(toJson(event));
                  if (event instanceof ProgressEvent.Completed
                      || event instanceof ProgressEvent.Failed) {
                    ctx.closeSession(1000, "done");
                    break;
                  }
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } catch (RuntimeException e) {
                log.debug("WS pump aborted: {}", e.getMessage());
              } finally {
                listener.unsubscribe(queue);
              }
            },
            "ws-progress-" + id);
    pump.setDaemon(true);
    pump.start();
  }

  public void download(Context ctx) {
    Job job = lookupOrError(ctx);
    if (job == null) {
      return;
    }
    Path output = job.outputPath();
    if (!Files.isRegularFile(output)) {
      ctx.status(HttpStatus.GONE).result("Output file no longer available");
      return;
    }

    long size;
    InputStream raw;
    try {
      size = Files.size(output);
      raw = Files.newInputStream(output);
    } catch (IOException e) {
      log.error("Failed to open output for job {}", job.id(), e);
      ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to read output");
      return;
    }

    UUID id = job.id();
    Path workDir = job.workDir();
    InputStream stream =
        new FilterInputStream(raw) {
          @Override
          public void close() throws IOException {
            try {
              super.close();
            } finally {
              registry.remove(id);
              dev.sakashita.tateyokopdf.web.lifecycle.WorkDirs.deleteQuietly(workDir);
              log.debug("Cleaned up job {} after download", id);
            }
          }
        };

    ctx.contentType("application/pdf");
    ctx.header(
        "Content-Disposition",
        "attachment; filename*=UTF-8''" + urlEncode(downloadName(job.originalName())));
    ctx.header("Content-Length", Long.toString(size));
    ctx.result(stream);
  }

  private @org.jspecify.annotations.Nullable Job lookupOrError(Context ctx) {
    UUID id;
    try {
      id = UUID.fromString(ctx.pathParam("id"));
    } catch (IllegalArgumentException e) {
      ctx.status(HttpStatus.NOT_FOUND).result("Job not found");
      return null;
    }
    Job job = registry.find(id).orElse(null);
    if (job == null) {
      ctx.status(HttpStatus.NOT_FOUND).result("Job not found");
      return null;
    }
    return job;
  }

  private static String toJson(ProgressEvent event) {
    return switch (event) {
      case ProgressEvent.Started s -> "{\"type\":\"started\",\"total\":" + s.total() + "}";
      case ProgressEvent.Progress p ->
          "{\"type\":\"progress\",\"current\":" + p.current() + ",\"total\":" + p.total() + "}";
      case ProgressEvent.Completed c -> "{\"type\":\"completed\"}";
      case ProgressEvent.Failed f ->
          "{\"type\":\"failed\",\"message\":\"" + jsonEscape(f.message()) + "\"}";
    };
  }

  private static String jsonEscape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
  }

  private static String downloadName(String originalName) {
    String base = originalName.replaceFirst("(?i)\\.pdf$", "");
    return base + "_spread.pdf";
  }

  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
