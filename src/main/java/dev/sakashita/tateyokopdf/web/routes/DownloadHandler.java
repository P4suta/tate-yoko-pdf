package dev.sakashita.tateyokopdf.web.routes;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.lifecycle.WorkDirs;
import io.javalin.http.Context;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streams a finished job's output PDF to the client and cleans up the work directory + registry
 * entry once the client has consumed the stream. Cleanup is anchored on {@code close()} of the
 * filtered stream — this is the only place that owns the lifecycle of the on-disk output.
 */
public final class DownloadHandler {

  private static final Logger log = LoggerFactory.getLogger(DownloadHandler.class);

  private final JobRegistry registry;

  public DownloadHandler(JobRegistry registry) {
    this.registry = registry;
  }

  public void serve(Context ctx, Job job) {
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
              WorkDirs.deleteQuietly(workDir);
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

  private static String downloadName(String originalName) {
    String base = originalName.replaceFirst("(?i)\\.pdf$", "");
    return base + "_spread.pdf";
  }

  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
