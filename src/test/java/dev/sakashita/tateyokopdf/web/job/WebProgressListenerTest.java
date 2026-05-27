package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Test;

final class WebProgressListenerTest {

  private static Job sampleJob() {
    return new Job(
        UUID.randomUUID(),
        Path.of("/w"),
        Path.of("/in"),
        Path.of("/out"),
        "x.pdf",
        Instant.now(),
        "trace-001");
  }

  @Test
  void emptySubscriberReceivesNoBackfill() {
    var l = new WebProgressListener(sampleJob());
    BlockingQueue<ProgressEvent> q = l.subscribe();
    assertThat(q).isEmpty();
  }

  @Test
  void lateSubscriberReceivesHistory() {
    var l = new WebProgressListener(sampleJob());
    l.onStart(2);
    l.onSpreadComplete(1, 2);
    BlockingQueue<ProgressEvent> q = l.subscribe();
    assertThat(q)
        .containsExactly(
            new ProgressEvent.Started(2, "trace-001"),
            new ProgressEvent.Progress(1, 2, "trace-001"));
  }

  @Test
  void completedTriggersTerminalAndJobStatusCompleted() {
    Job job = sampleJob();
    var l = new WebProgressListener(job);
    l.onStart(1);
    l.onSpreadComplete(1, 1);
    l.onComplete(123L);
    assertThat(job.status()).isInstanceOf(JobStatus.Completed.class);
  }

  @Test
  void failWithSpreadExceptionPropagatesKindAndMessage() {
    Job job = sampleJob();
    var l = new WebProgressListener(job);
    l.fail(SpreadException.of(ErrorKind.PDF_CORRUPTED));
    assertThat(job.status()).isInstanceOf(JobStatus.Failed.class);
    BlockingQueue<ProgressEvent> q = l.subscribe();
    ProgressEvent ev = q.poll();
    assertThat(ev).isInstanceOf(ProgressEvent.Failed.class);
    ProgressEvent.Failed f = (ProgressEvent.Failed) ev;
    assertThat(f.errorKind()).isEqualTo(ErrorKind.PDF_CORRUPTED);
    assertThat(f.traceId()).isEqualTo("trace-001");
  }

  @Test
  void failWithStringMapsToInternalKind() {
    Job job = sampleJob();
    var l = new WebProgressListener(job);
    l.fail("plain message");
    BlockingQueue<ProgressEvent> q = l.subscribe();
    ProgressEvent.Failed f = (ProgressEvent.Failed) q.poll();
    assertThat(f.errorKind()).isEqualTo(ErrorKind.INTERNAL);
    assertThat(f.message()).isEqualTo("plain message");
  }

  @Test
  void subscribeAfterTerminalDoesNotEnqueueFutureEvents() {
    Job job = sampleJob();
    var l = new WebProgressListener(job);
    l.fail(SpreadException.of(ErrorKind.PDF_CORRUPTED));
    BlockingQueue<ProgressEvent> q = l.subscribe();
    assertThat(q).hasSize(1);
    assertThat(q.poll()).isInstanceOf(ProgressEvent.Failed.class);
  }

  @Test
  void liveSubscriberReceivesSubsequentPublishes() {
    var l = new WebProgressListener(sampleJob());
    BlockingQueue<ProgressEvent> q = l.subscribe();
    l.onStart(2);
    l.onSpreadComplete(1, 2);
    assertThat(q).hasSize(2);
  }

  @Test
  void unsubscribePreventsFurtherDelivery() {
    var l = new WebProgressListener(sampleJob());
    BlockingQueue<ProgressEvent> q = l.subscribe();
    l.unsubscribe(q);
    l.onStart(1);
    assertThat(q).isEmpty();
  }
}
