package dev.sakashita.tateyokopdf.testfixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sakashita.tateyokopdf.web.job.ProgressEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Test-side WebSocket client for the job progress endpoint. Wraps {@link
 * java.net.http.HttpClient#newWebSocketBuilder()} so integration tests can drive the full
 * submit-then-watch-progress-then-download flow against a real running Javalin server.
 *
 * <p>Why not OkHttp's WebSocket: javalin-testtools does not bring OkHttp as a transitive (verified
 * via {@code gradle dependencies}) and we want to keep the test stack thin. JDK 11+'s built-in
 * WebSocket client is enough.
 */
public final class JobWsClient implements AutoCloseable {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final BlockingQueue<String> messages;
  private final AtomicInteger closeStatus;
  private final HttpClient http;
  private final WebSocket ws;

  private JobWsClient(
      HttpClient http, WebSocket ws, BlockingQueue<String> messages, AtomicInteger closeStatus) {
    this.http = http;
    this.ws = ws;
    this.messages = messages;
    this.closeStatus = closeStatus;
  }

  public static JobWsClient connect(int port, UUID jobId) {
    HttpClient http = HttpClient.newHttpClient();
    // The queue and close-status holders are created up-front so the Sink can be bound to
    // them *before* the WebSocket open handshake completes. Otherwise an early Started frame
    // may arrive via Sink.onText before `bindTo(client)` runs, and the message is silently
    // dropped — surfacing later as a hung second-WS-client test under load.
    var messages = new LinkedBlockingQueue<String>();
    var closeStatus = new AtomicInteger(-1);
    var sink = new Sink(messages, closeStatus);
    WebSocket ws =
        http.newWebSocketBuilder()
            .buildAsync(URI.create("ws://localhost:" + port + "/ws/jobs/" + jobId), sink)
            .join();
    return new JobWsClient(http, ws, messages, closeStatus);
  }

  /** Pop the next text frame (parsed as a {@link ProgressEvent}) within the timeout, or null. */
  public @Nullable ProgressEvent nextEvent(Duration timeout) throws InterruptedException {
    String text = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (text == null) {
      return null;
    }
    try {
      return MAPPER.readValue(text, ProgressEvent.class);
    } catch (IOException e) {
      throw new AssertionError("malformed WS frame: " + text, e);
    }
  }

  public boolean isClosed() {
    return closeStatus.get() >= 0;
  }

  public int closeStatus() {
    return closeStatus.get();
  }

  @Override
  public void close() {
    try {
      if (!ws.isOutputClosed()) {
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "test done").orTimeout(2, TimeUnit.SECONDS).join();
      }
    } catch (RuntimeException ignored) {
      // best-effort; the server may already have closed
    } finally {
      ws.abort();
      http.close();
    }
  }

  /** Force-aborts the underlying socket without a clean close frame — simulates a network drop. */
  public void abort() {
    ws.abort();
    http.close();
  }

  /** Listener with direct references to the message queue and close-status slot. */
  private static final class Sink implements WebSocket.Listener {

    private final BlockingQueue<String> messages;
    private final AtomicInteger closeStatus;
    private final StringBuilder buffer = new StringBuilder();

    Sink(BlockingQueue<String> messages, AtomicInteger closeStatus) {
      this.messages = messages;
      this.closeStatus = closeStatus;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        messages.offer(buffer.toString());
        buffer.setLength(0);
      }
      webSocket.request(1);
      return java.util.concurrent.CompletableFuture.completedStage(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      closeStatus.set(statusCode);
      return java.util.concurrent.CompletableFuture.completedStage(null);
    }
  }
}
