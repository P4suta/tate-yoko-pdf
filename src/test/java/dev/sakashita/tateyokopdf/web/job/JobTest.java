package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class JobTest {

  @Test
  void newJobIsPending() {
    var job = sampleJob();
    assertThat(job.status()).isInstanceOf(JobStatus.Pending.class);
  }

  @Test
  void allFieldsExposed() {
    UUID id = UUID.randomUUID();
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    var job =
        new Job(
            id,
            Path.of("/tmp/work"),
            Path.of("/tmp/in.pdf"),
            Path.of("/tmp/out.pdf"),
            "doc.pdf",
            t);
    assertThat(job.id()).isEqualTo(id);
    assertThat(job.workDir()).isEqualTo(Path.of("/tmp/work"));
    assertThat(job.inputPath()).isEqualTo(Path.of("/tmp/in.pdf"));
    assertThat(job.outputPath()).isEqualTo(Path.of("/tmp/out.pdf"));
    assertThat(job.originalName()).isEqualTo("doc.pdf");
    assertThat(job.createdAt()).isEqualTo(t);
  }

  @Test
  void setStatusMutates() {
    var job = sampleJob();
    job.setStatus(new JobStatus.Running(1, 4));
    assertThat(job.status()).isInstanceOf(JobStatus.Running.class);
    job.setStatus(new JobStatus.Completed());
    assertThat(job.status()).isInstanceOf(JobStatus.Completed.class);
  }

  private static Job sampleJob() {
    return new Job(
        UUID.randomUUID(),
        Path.of("/tmp/work"),
        Path.of("/tmp/in.pdf"),
        Path.of("/tmp/out.pdf"),
        "x.pdf",
        Instant.now());
  }
}
