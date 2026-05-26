package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Test;

final class WebProgressListenerTest {

  private static Job sampleJob() {
    return new Job(
        UUID.randomUUID(), Path.of("/w"), Path.of("/in"), Path.of("/out"), "x.pdf", Instant.now());
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
    assertThat(q).containsExactly(new ProgressEvent.Started(2), new ProgressEvent.Progress(1, 2));
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
  void failedTriggersTerminalAndJobStatusFailed() {
    Job job = sampleJob();
    var l = new WebProgressListener(job);
    l.fail("kaboom");
    assertThat(job.status()).isInstanceOf(JobStatus.Failed.class);
    JobStatus.Failed f = (JobStatus.Failed) job.status();
    assertThat(f.message()).isEqualTo("kaboom");
  }

  @Test
  void subscribeAfterTerminalDoesNotEnqueueFutureEvents() {
    Job job = sampleJob();
    var l = new WebProgressListener(job);
    l.fail("done");
    BlockingQueue<ProgressEvent> q = l.subscribe();
    // history backfilled
    assertThat(q).hasSize(1);
    // further publishes (if any) won't reach this subscriber because subscribers list rejected it
    // (note: there are no public methods to publish after terminal, so this asserts only history)
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
