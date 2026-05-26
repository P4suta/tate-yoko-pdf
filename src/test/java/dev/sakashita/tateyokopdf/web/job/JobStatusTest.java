package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class JobStatusTest {

  @Test
  void pendingHasNoFields() {
    assertThat(new JobStatus.Pending()).isEqualTo(new JobStatus.Pending());
  }

  @Test
  void runningHoldsCounters() {
    var r = new JobStatus.Running(2, 5);
    assertThat(r.current()).isEqualTo(2);
    assertThat(r.total()).isEqualTo(5);
  }

  @Test
  void completedIsValueEquality() {
    assertThat(new JobStatus.Completed()).isEqualTo(new JobStatus.Completed());
  }

  @Test
  void failedHoldsMessage() {
    var f = new JobStatus.Failed("dead");
    assertThat(f.message()).isEqualTo("dead");
  }
}
