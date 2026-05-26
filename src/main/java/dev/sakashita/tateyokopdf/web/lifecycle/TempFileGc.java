package dev.sakashita.tateyokopdf.web.lifecycle;

import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TempFileGc {

  private static final Logger log = LoggerFactory.getLogger(TempFileGc.class);

  private final JobRegistry registry;
  private final Duration ttl;
  private final Duration sweepInterval;
  private final Supplier<Instant> nowSupplier;
  private final ScheduledExecutorService scheduler;
  private @Nullable ScheduledFuture<?> task;

  public TempFileGc(JobRegistry registry, Duration ttl, Duration sweepInterval) {
    this(registry, ttl, sweepInterval, Instant::now);
  }

  public TempFileGc(
      JobRegistry registry, Duration ttl, Duration sweepInterval, Supplier<Instant> nowSupplier) {
    this.registry = registry;
    this.ttl = ttl;
    this.sweepInterval = sweepInterval;
    this.nowSupplier = nowSupplier;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "tate-yoko-gc");
              t.setDaemon(true);
              return t;
            });
  }

  public void start() {
    task =
        scheduler.scheduleAtFixedRate(
            this::sweep, sweepInterval.toMillis(), sweepInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  public void stop() {
    if (task != null) {
      task.cancel(false);
    }
    scheduler.shutdownNow();
  }

  private void sweep() {
    try {
      Instant cutoff = nowSupplier.get().minus(ttl);
      List<Job> expired = registry.removeOlderThan(cutoff);
      for (Job job : expired) {
        log.info("GC expired job {} (workDir={})", job.id(), job.workDir());
        WorkDirs.deleteQuietly(job.workDir());
      }
    } catch (RuntimeException e) {
      log.warn("GC sweep failed: {}", e.getMessage());
    }
  }
}
