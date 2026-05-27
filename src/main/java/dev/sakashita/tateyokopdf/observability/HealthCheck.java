package dev.sakashita.tateyokopdf.observability;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.IntSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Best-effort liveness/readiness probes. Cheap, deterministic, and never throws — every check
 * captures its own failure into the resulting report.
 */
public final class HealthCheck {

  private static final Logger log = LoggerFactory.getLogger(HealthCheck.class);
  private static final long DEFAULT_MIN_FREE_BYTES = 100L * 1024 * 1024;

  public enum Status {
    UP,
    DOWN
  }

  public record Check(Status status, @Nullable String detail) {}

  public record Report(Status status, Map<String, Check> checks) {}

  private final IntSupplier jobCount;
  private final ThreadPoolExecutor workers;
  private final Path workDirRoot;
  private final long minFreeBytes;

  public HealthCheck(
      IntSupplier jobCount, ThreadPoolExecutor workers, Path workDirRoot, long minFreeBytes) {
    this.jobCount = jobCount;
    this.workers = workers;
    this.workDirRoot = workDirRoot;
    this.minFreeBytes = minFreeBytes;
  }

  public HealthCheck(IntSupplier jobCount, ThreadPoolExecutor workers) {
    this(jobCount, workers, defaultWorkDirRoot(), resolveMinFreeBytes());
  }

  public Report run() {
    Map<String, Check> checks = new LinkedHashMap<>();
    Status overall = Status.UP;

    record CheckEntry(String name, java.util.function.Supplier<Check> runner) {}

    for (CheckEntry entry :
        new CheckEntry[] {
          new CheckEntry("workDirWritable", this::checkWorkDirWritable),
          new CheckEntry("diskFreeBytes", this::checkDiskFree),
          new CheckEntry("executorHealthy", this::checkExecutor),
          new CheckEntry("jobRegistry", this::checkJobRegistry),
        }) {
      Check c;
      try {
        c = entry.runner().get();
      } catch (RuntimeException e) {
        log.warn("Health check {} threw", entry.name(), e);
        c = new Check(Status.DOWN, e.getClass().getSimpleName());
      }
      checks.put(entry.name(), c);
      if (c.status() == Status.DOWN) {
        overall = Status.DOWN;
      }
    }

    return new Report(overall, Map.copyOf(checks));
  }

  private Check checkWorkDirWritable() {
    try {
      if (!Files.isDirectory(workDirRoot)) {
        return new Check(Status.DOWN, "workDirRoot is not a directory: " + workDirRoot);
      }
      Path probe = Files.createTempFile(workDirRoot, "health-", ".tmp");
      Files.deleteIfExists(probe);
      return new Check(Status.UP, null);
    } catch (IOException e) {
      return new Check(Status.DOWN, "createTempFile failed: " + e.getClass().getSimpleName());
    }
  }

  private Check checkDiskFree() {
    try {
      FileStore fs = Files.getFileStore(workDirRoot);
      long usable = fs.getUsableSpace();
      if (usable < minFreeBytes) {
        return new Check(Status.DOWN, "usable=" + usable + " < min=" + minFreeBytes);
      }
      return new Check(Status.UP, "usable=" + usable);
    } catch (IOException e) {
      return new Check(Status.DOWN, "getFileStore failed");
    }
  }

  private Check checkExecutor() {
    if (workers.isShutdown() || workers.isTerminated()) {
      return new Check(Status.DOWN, "executor is shut down");
    }
    int queued = workers.getQueue().size();
    int capacity = workers.getMaximumPoolSize();
    if (queued > capacity * 100L) {
      return new Check(Status.DOWN, "queue backlog: " + queued);
    }
    return new Check(Status.UP, "active=" + workers.getActiveCount() + ", queued=" + queued);
  }

  private Check checkJobRegistry() {
    return new Check(Status.UP, "size=" + jobCount.getAsInt());
  }

  private static Path defaultWorkDirRoot() {
    String override = System.getenv("TATE_YOKO_WORKDIR_ROOT");
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    return Path.of(System.getProperty("java.io.tmpdir", "/tmp"));
  }

  private static long resolveMinFreeBytes() {
    String env = System.getenv("TATE_YOKO_HEALTH_MIN_FREE_MB");
    if (env == null || env.isBlank()) {
      return DEFAULT_MIN_FREE_BYTES;
    }
    try {
      return Long.parseLong(env.trim()) * 1024L * 1024L;
    } catch (NumberFormatException e) {
      log.warn("Invalid TATE_YOKO_HEALTH_MIN_FREE_MB={}, using default", env);
      return DEFAULT_MIN_FREE_BYTES;
    }
  }
}
