package dev.sakashita.tateyokopdf.web.job;

import dev.sakashita.tateyokopdf.application.ProgressListener;
import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Owns the ProgressEvent stream for a single job. History + the {@code terminal} flag are the only
 * source of truth for "where is this job"; readers (WS pump, idle shutdown's busy check) consult
 * this listener instead of any duplicate status field on {@link Job}.
 */
public final class WebProgressListener implements ProgressListener {

  private final Job job;
  private final List<BlockingQueue<ProgressEvent>> subscribers = new CopyOnWriteArrayList<>();
  private final List<ProgressEvent> history = new CopyOnWriteArrayList<>();
  private volatile boolean terminal = false;

  public WebProgressListener(Job job) {
    this.job = job;
  }

  /**
   * Subscribe a new WS client. The queue is back-filled with history. {@code synchronized} against
   * {@link #publish} so a late subscriber cannot miss an in-flight terminal event in the window
   * between reading {@code history} and checking {@code terminal} — a race that {@link
   * CopyOnWriteArrayList} + {@code volatile} cannot defend on its own.
   */
  public synchronized BlockingQueue<ProgressEvent> subscribe() {
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

  /** Snapshot of every event published so far. */
  public List<ProgressEvent> history() {
    return List.copyOf(history);
  }

  /** True once a {@code Completed} or {@code Failed} event has been published. */
  public boolean terminal() {
    return terminal;
  }

  private synchronized void publish(ProgressEvent event) {
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
    publish(new ProgressEvent.Started(totalSpreads, job.traceId()));
  }

  @Override
  public void onSpreadComplete(int currentSpread, int totalSpreads) {
    publish(new ProgressEvent.Progress(currentSpread, totalSpreads, job.traceId()));
  }

  @Override
  public void onComplete(long elapsedMillis) {
    publish(new ProgressEvent.Completed(job.traceId()));
  }

  public void fail(SpreadException e) {
    publish(new ProgressEvent.Failed(e.kind(), e.userMessage(), job.traceId()));
  }

  /** Convenience for callers that only have a plain message string (defaults to INTERNAL). */
  public void fail(String message) {
    publish(new ProgressEvent.Failed(ErrorKind.INTERNAL, message, job.traceId()));
  }
}
