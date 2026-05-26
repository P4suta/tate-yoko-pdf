package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.observability.HealthCheck.Check;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HealthCheckTest {

  private ThreadPoolExecutor workers =
      new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

  @AfterEach
  void shutdownWorkers() {
    workers.shutdownNow();
  }

  @Test
  void allChecksUpForFreshSetup(@TempDir Path tmp) {
    var hc = new HealthCheck(new JobRegistry(), workers, tmp, 1L);
    var report = hc.run();
    assertThat(report.status()).isEqualTo(HealthCheck.Status.UP);
    assertThat(report.checks())
        .containsKeys("workDirWritable", "diskFreeBytes", "executorHealthy", "jobRegistry");
  }

  @Test
  void diskFreeDownWhenThresholdImpossiblyHigh(@TempDir Path tmp) {
    var hc = new HealthCheck(new JobRegistry(), workers, tmp, Long.MAX_VALUE);
    var report = hc.run();
    assertThat(report.status()).isEqualTo(HealthCheck.Status.DOWN);
    Check diskCheck = Objects.requireNonNull(report.checks().get("diskFreeBytes"));
    assertThat(diskCheck.status()).isEqualTo(HealthCheck.Status.DOWN);
  }

  @Test
  void workDirDownWhenPathIsNotADirectory(@TempDir Path tmp) throws Exception {
    Path file = Files.createFile(tmp.resolve("notadir"));
    var hc = new HealthCheck(new JobRegistry(), workers, file, 1L);
    var report = hc.run();
    Check workDir = Objects.requireNonNull(report.checks().get("workDirWritable"));
    assertThat(workDir.status()).isEqualTo(HealthCheck.Status.DOWN);
    assertThat(report.status()).isEqualTo(HealthCheck.Status.DOWN);
  }

  @Test
  void executorDownAfterShutdown(@TempDir Path tmp) {
    workers.shutdown();
    var hc = new HealthCheck(new JobRegistry(), workers, tmp, 1L);
    var report = hc.run();
    Check exec = Objects.requireNonNull(report.checks().get("executorHealthy"));
    assertThat(exec.status()).isEqualTo(HealthCheck.Status.DOWN);
    assertThat(report.status()).isEqualTo(HealthCheck.Status.DOWN);
  }

  @Test
  void jobRegistrySizeAppearsInDetail(@TempDir Path tmp) {
    var reg = new JobRegistry();
    reg.register(tmp, tmp.resolve("in"), tmp.resolve("out"), "a.pdf");
    var hc = new HealthCheck(reg, workers, tmp, 1L);
    var report = hc.run();
    Check jobReg = Objects.requireNonNull(report.checks().get("jobRegistry"));
    assertThat(jobReg.detail()).contains("size=1");
  }
}
