package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class JobRegistryBusyTest {

  @Test
  void freshlyRegisteredPendingJobCountsAsBusy() {
    var reg = new JobRegistry();
    reg.register(Path.of("/w"), Path.of("/i"), Path.of("/o"), "a.pdf");
    assertThat(reg.hasRunningJobs()).isTrue();
  }

  @Test
  void emptyRegistryIsNotBusy() {
    assertThat(new JobRegistry().hasRunningJobs()).isFalse();
  }

  @Test
  void completedJobDoesNotCountAsBusy() {
    var reg = new JobRegistry();
    var job = reg.register(Path.of("/w"), Path.of("/i"), Path.of("/o"), "a.pdf");
    job.setStatus(new JobStatus.Completed());
    assertThat(reg.hasRunningJobs()).isFalse();
  }

  @Test
  void failedJobDoesNotCountAsBusy() {
    var reg = new JobRegistry();
    var job = reg.register(Path.of("/w"), Path.of("/i"), Path.of("/o"), "a.pdf");
    job.setStatus(new JobStatus.Failed("dead"));
    assertThat(reg.hasRunningJobs()).isFalse();
  }

  @Test
  void runningJobCountsAsBusy() {
    var reg = new JobRegistry();
    var job = reg.register(Path.of("/w"), Path.of("/i"), Path.of("/o"), "a.pdf");
    job.setStatus(new JobStatus.Running(1, 5));
    assertThat(reg.hasRunningJobs()).isTrue();
  }
}
