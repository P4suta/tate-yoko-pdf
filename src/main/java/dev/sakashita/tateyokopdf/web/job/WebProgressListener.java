package dev.sakashita.tateyokopdf.web.job;

import dev.sakashita.tateyokopdf.application.ProgressListener;
import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public final class WebProgressListener implements ProgressListener {

  private final Job job;
  private final List<BlockingQueue<ProgressEvent>> subscribers = new CopyOnWriteArrayList<>();
  private final List<ProgressEvent> history = new CopyOnWriteArrayList<>();
  private volatile boolean terminal = false;

  public WebProgressListener(Job job) {
    this.job = job;
  }

  /** Subscribe a new WS client. The queue is back-filled with history. */
  public BlockingQueue<ProgressEvent> subscribe() {
    BlockingQueue<ProgressEvent> q = new LinkedBlockingQueue<>();
    q.addAll(history);
    if (!terminal) {
      subscribers.add(q);
    }
    return q;
  }

  public void unsubscribe(BlockingQueue<ProgressEvent> q) {
    subscribers.remove(q);
  }

  private void publish(ProgressEvent event) {
    history.add(event);
    if (event instanceof ProgressEvent.Completed || event instanceof ProgressEvent.Failed) {
      terminal = true;
    }
    for (BlockingQueue<ProgressEvent> q : subscribers) {
      // Unbounded LinkedBlockingQueue#offer never refuses; the boolean return
      // is captured to silence SpotBugs RV_RETURN_VALUE_IGNORED_BAD_PRACTICE.
      var accepted = q.offer(event);
      assert accepted;
    }
  }

  @Override
  public void onStart(int totalSpreads) {
    job.setStatus(new JobStatus.Running(0, totalSpreads));
    publish(new ProgressEvent.Started(totalSpreads, job.traceId()));
  }

  @Override
  public void onSpreadComplete(int currentSpread, int totalSpreads) {
    job.setStatus(new JobStatus.Running(currentSpread, totalSpreads));
    publish(new ProgressEvent.Progress(currentSpread, totalSpreads, job.traceId()));
  }

  @Override
  public void onComplete(long elapsedMillis) {
    job.setStatus(new JobStatus.Completed());
    publish(new ProgressEvent.Completed(job.traceId()));
  }

  public void fail(SpreadException e) {
    job.setStatus(new JobStatus.Failed(e.userMessage()));
    publish(new ProgressEvent.Failed(e.kind(), e.userMessage(), job.traceId()));
  }

  /** Convenience for callers that only have a plain message string (defaults to INTERNAL). */
  public void fail(String message) {
    job.setStatus(new JobStatus.Failed(message));
    publish(new ProgressEvent.Failed(ErrorKind.INTERNAL, message, job.traceId()));
  }
}
