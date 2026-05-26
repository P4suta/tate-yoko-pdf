package dev.sakashita.tateyokopdf.web.routes;

import dev.sakashita.tateyokopdf.application.SpreadOptions;
import dev.sakashita.tateyokopdf.application.SpreadService;
import dev.sakashita.tateyokopdf.domain.model.ReadingDirection;
import dev.sakashita.tateyokopdf.domain.service.SpreadLayoutCalculator;
import dev.sakashita.tateyokopdf.domain.strategy.CoverSinglePagination;
import dev.sakashita.tateyokopdf.domain.strategy.PaginationStrategy;
import dev.sakashita.tateyokopdf.domain.strategy.StandardPagination;
import dev.sakashita.tateyokopdf.infrastructure.pdfbox.PdfBoxDocumentFactory;
import dev.sakashita.tateyokopdf.observability.RequestTracingFilter;
import dev.sakashita.tateyokopdf.observability.SafeExecutor;
import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import dev.sakashita.tateyokopdf.port.exception.SpreadException;
import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.job.JobStatus;
import dev.sakashita.tateyokopdf.web.job.ProgressEvent;
import dev.sakashita.tateyokopdf.web.job.WebProgressListener;
import dev.sakashita.tateyokopdf.web.job.WsCloseCodes;
import dev.sakashita.tateyokopdf.web.job.WsFrames;
import dev.sakashita.tateyokopdf.web.upload.UploadValidator;
import io.javalin.http.Context;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobController {

  private static final Logger log = LoggerFactory.getLogger(JobController.class);
  private static final long WS_POLL_SECONDS = 1L;

  private final JobRegistry registry;
  private final ViewRenderer renderer;
  private final ExecutorService executor;
  private final UploadValidator uploadValidator;

  public JobController(
      JobRegistry registry,
      ViewRenderer renderer,
      ExecutorService executor,
      UploadValidator uploadValidator) {
    this.registry = registry;
    this.renderer = renderer;
    this.executor = executor;
    this.uploadValidator = uploadValidator;
  }

  public void submit(Context ctx) {
    UploadedFile uploaded = ctx.uploadedFile("pdf");
    String originalName = uploadValidator.validate(uploaded);

    String dirParam = ctx.formParamAsClass("direction", String.class).getOrDefault("RTL");
    ReadingDirection direction = parseDirection(dirParam);
    boolean coverSingle = ctx.formParam("coverSingle") != null;
    String traceId = traceIdOf(ctx);

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

    Job job = registry.register(workDir, inputPath, outputPath, originalName, traceId);
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
            SafeExecutor.guarded(
                () -> service.execute(options), listener::fail, "job=" + job.id()));

    ctx.redirect("/jobs/" + job.id() + "/progress");
  }

  public void showProgress(Context ctx) {
    Job job = lookup(ctx);
    renderer.renderHtml(ctx, "progress.jte", Map.of("job", job));
  }

  public void showResult(Context ctx) {
    Job job = lookup(ctx);
    if (!(job.status() instanceof JobStatus.Completed)) {
      ctx.redirect("/jobs/" + job.id() + "/progress");
      return;
    }
    renderer.renderHtml(ctx, "result.jte", Map.of("job", job));
  }

  public void onProgressWs(WsConnectContext ctx) {
    UUID id;
    try {
      id = UUID.fromString(ctx.pathParam("id"));
    } catch (IllegalArgumentException e) {
      ctx.send(WsFrames.error(ErrorKind.JOB_NOT_FOUND, "ジョブが見つかりません", "-"));
      ctx.closeSession(WsCloseCodes.JOB_NOT_FOUND, "Job not found");
      return;
    }
    WebProgressListener listener = registry.listener(id).orElse(null);
    if (listener == null) {
      ctx.send(WsFrames.error(ErrorKind.JOB_NOT_FOUND, "ジョブが見つかりません", "-"));
      ctx.closeSession(WsCloseCodes.JOB_NOT_FOUND, "Job not found");
      return;
    }
    BlockingQueue<ProgressEvent> queue = listener.subscribe();
    Thread pump = new Thread(() -> pump(ctx, listener, queue, id), "ws-progress-" + id);
    pump.setDaemon(true);
    pump.start();
  }

  private static void pump(
      WsConnectContext ctx,
      WebProgressListener listener,
      BlockingQueue<ProgressEvent> queue,
      UUID id) {
    try {
      while (ctx.session.isOpen()) {
        ProgressEvent event = queue.poll(WS_POLL_SECONDS, TimeUnit.SECONDS);
        if (event == null) {
          continue;
        }
        ctx.send(WsFrames.progress(event));
        if (event instanceof ProgressEvent.Completed) {
          ctx.closeSession(WsCloseCodes.NORMAL, "done");
          break;
        }
        if (event instanceof ProgressEvent.Failed f) {
          ctx.closeSession(WsCloseCodes.forErrorKind(f.errorKind()), f.errorKind().name());
          break;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException e) {
      log.debug("WS pump for {} aborted: {}", id, e.getMessage());
      try {
        ctx.send(WsFrames.error(ErrorKind.INTERNAL, "WS 内部エラー", "-"));
        ctx.closeSession(WsCloseCodes.INTERNAL, "internal");
      } catch (RuntimeException ignored) {
        // best-effort
      }
    } finally {
      listener.unsubscribe(queue);
    }
  }

  public void download(Context ctx) {
    Job job = lookup(ctx);
    Path output = job.outputPath();
    if (!Files.isRegularFile(output)) {
      throw SpreadException.of(ErrorKind.JOB_OUTPUT_GONE);
    }

    long size;
    InputStream raw;
    try {
      size = Files.size(output);
      raw = Files.newInputStream(output);
    } catch (IOException e) {
      throw SpreadException.withDetail(ErrorKind.INTERNAL, "read output failed", e);
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

  private Job lookup(Context ctx) {
    UUID id;
    try {
      id = UUID.fromString(ctx.pathParam("id"));
    } catch (IllegalArgumentException e) {
      throw SpreadException.of(ErrorKind.JOB_NOT_FOUND);
    }
    return registry.find(id).orElseThrow(() -> SpreadException.of(ErrorKind.JOB_NOT_FOUND));
  }

  private static ReadingDirection parseDirection(String value) {
    try {
      return ReadingDirection.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw SpreadException.withDetail(
          ErrorKind.INVALID_PARAMETER, "direction must be RTL or LTR but was: " + value, e);
    }
  }

  private static String traceIdOf(Context ctx) {
    String t = ctx.attribute(RequestTracingFilter.ATTR_TRACE_ID);
    return t != null ? t : "-";
  }

  private static String downloadName(String originalName) {
    String base = originalName.replaceFirst("(?i)\\.pdf$", "");
    return base + "_spread.pdf";
  }

  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
