package dev.sakashita.tateyokopdf.web.routes;

import dev.sakashita.tateyokopdf.application.SpreadOptions;
import dev.sakashita.tateyokopdf.application.SpreadService;
import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.domain.model.ReadingDirection;
import dev.sakashita.tateyokopdf.domain.service.SpreadLayoutCalculator;
import dev.sakashita.tateyokopdf.infrastructure.pdfbox.PdfBoxDocumentFactory;
import dev.sakashita.tateyokopdf.observability.SafeExecutor;
import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobJsonMapping;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.job.ProgressEvent;
import dev.sakashita.tateyokopdf.web.job.WebProgressListener;
import dev.sakashita.tateyokopdf.web.job.WsCloseCodes;
import dev.sakashita.tateyokopdf.web.observability.RequestTracingFilter;
import dev.sakashita.tateyokopdf.web.upload.UploadValidator;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import io.javalin.websocket.WsConnectContext;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP/WS routing entry points for jobs. Upload staging is delegated to {@link JobFactory} and
 * download streaming to {@link DownloadHandler} so this class reads as a thin router rather than a
 * mix of routing + filesystem + lifecycle concerns.
 */
public final class JobController {

  private static final Logger log = LoggerFactory.getLogger(JobController.class);
  private static final long WS_POLL_SECONDS = 1L;

  private final JobRegistry registry;
  private final ExecutorService executor;
  private final UploadValidator uploadValidator;
  private final JobFactory jobFactory;
  private final DownloadHandler downloadHandler;
  private final SafeExecutor safeExecutor;

  public JobController(
      JobRegistry registry,
      ExecutorService executor,
      UploadValidator uploadValidator,
      JobFactory jobFactory,
      DownloadHandler downloadHandler,
      SafeExecutor safeExecutor) {
    this.registry = registry;
    this.executor = executor;
    this.uploadValidator = uploadValidator;
    this.jobFactory = jobFactory;
    this.downloadHandler = downloadHandler;
    this.safeExecutor = safeExecutor;
  }

  public void submit(Context ctx) {
    UploadedFile uploaded = ctx.uploadedFile("pdf");
    String originalName = uploadValidator.validate(uploaded);
    // validate() throws when uploaded is null, so reaching here narrows it for NullAway.
    UploadedFile pdf = Objects.requireNonNull(uploaded, "uploaded");

    String dirParam = ctx.formParamAsClass("direction", String.class).getOrDefault("RTL");
    ReadingDirection direction = parseDirection(dirParam);
    boolean coverSingle = ctx.formParam("coverSingle") != null;
    String traceId = traceIdOf(ctx);

    Job job = jobFactory.stage(pdf, originalName, traceId);
    WebProgressListener listener =
        registry
            .listener(job.id())
            .orElseThrow(() -> new IllegalStateException("listener missing after register"));

    SpreadOptions options =
        new SpreadOptions(job.inputPath(), job.outputPath(), direction, coverSingle);
    SpreadService service =
        new SpreadService(new PdfBoxDocumentFactory(), new SpreadLayoutCalculator(), listener);

    // Fire-and-forget: the worker reports outcomes through `listener`; the
    // Future returned by submit() is intentionally discarded.
    executor.execute(
        safeExecutor.guarded(() -> service.execute(options), listener::fail, "job=" + job.id()));

    ctx.status(202);
    ctx.contentType("application/json");
    ctx.result("{\"id\":\"" + job.id() + "\"}");
  }

  public void onProgressWs(WsConnectContext ctx) {
    UUID id;
    try {
      id = UUID.fromString(ctx.pathParam("id"));
    } catch (IllegalArgumentException e) {
      ctx.send(JobJsonMapping.failedFrame(ErrorKind.JOB_NOT_FOUND, "ジョブが見つかりません", "-"));
      ctx.closeSession(WsCloseCodes.JOB_NOT_FOUND, "Job not found");
      return;
    }
    WebProgressListener listener = registry.listener(id).orElse(null);
    if (listener == null) {
      ctx.send(JobJsonMapping.failedFrame(ErrorKind.JOB_NOT_FOUND, "ジョブが見つかりません", "-"));
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
        ctx.send(JobJsonMapping.toJson(event));
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
        ctx.send(JobJsonMapping.failedFrame(ErrorKind.INTERNAL, "WS 内部エラー", "-"));
        ctx.closeSession(WsCloseCodes.INTERNAL, "internal");
      } catch (RuntimeException ignored) {
        // best-effort
      }
    } finally {
      listener.unsubscribe(queue);
    }
  }

  public void download(Context ctx) {
    downloadHandler.serve(ctx, lookup(ctx));
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
}
