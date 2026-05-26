package dev.sakashita.tateyokopdf.web.job;

import dev.sakashita.tateyokopdf.application.ProgressListener;
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

  /** Subscribe a new SSE client. The queue is back-filled with history. */
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
      q.offer(event);
    }
  }

  @Override
  public void onStart(int totalSpreads) {
    job.setStatus(new JobStatus.Running(0, totalSpreads));
    publish(new ProgressEvent.Started(totalSpreads));
  }

  @Override
  public void onSpreadComplete(int currentSpread, int totalSpreads) {
    job.setStatus(new JobStatus.Running(currentSpread, totalSpreads));
    publish(new ProgressEvent.Progress(currentSpread, totalSpreads));
  }

  @Override
  public void onComplete(long elapsedMillis) {
    job.setStatus(new JobStatus.Completed());
    publish(new ProgressEvent.Completed());
  }

  public void fail(String message) {
    job.setStatus(new JobStatus.Failed(message));
    publish(new ProgressEvent.Failed(message));
  }
}
