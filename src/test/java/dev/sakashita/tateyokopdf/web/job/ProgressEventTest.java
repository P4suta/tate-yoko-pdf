package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.port.exception.ErrorKind;
import org.junit.jupiter.api.Test;

final class ProgressEventTest {

  @Test
  void startedHoldsTotalAndTraceId() {
    var e = new ProgressEvent.Started(10, "abc");
    assertThat(e.total()).isEqualTo(10);
    assertThat(e.traceId()).isEqualTo("abc");
  }

  @Test
  void progressHoldsCountersAndTraceId() {
    var e = new ProgressEvent.Progress(3, 7, "xyz");
    assertThat(e.current()).isEqualTo(3);
    assertThat(e.total()).isEqualTo(7);
    assertThat(e.traceId()).isEqualTo("xyz");
  }

  @Test
  void completedHoldsTraceId() {
    var e = new ProgressEvent.Completed("done");
    assertThat(e.traceId()).isEqualTo("done");
  }

  @Test
  void failedHoldsKindMessageAndTraceId() {
    var e = new ProgressEvent.Failed(ErrorKind.PDF_CORRUPTED, "oops", "t-1");
    assertThat(e.errorKind()).isEqualTo(ErrorKind.PDF_CORRUPTED);
    assertThat(e.message()).isEqualTo("oops");
    assertThat(e.traceId()).isEqualTo("t-1");
  }

  @Test
  void sealedSwitchHandlesAllCases() {
    ProgressEvent ev = new ProgressEvent.Started(1, "t");
    String label =
        switch (ev) {
          case ProgressEvent.Started s -> "started";
          case ProgressEvent.Progress p -> "progress";
          case ProgressEvent.Completed c -> "completed";
          case ProgressEvent.Failed f -> "failed";
        };
    assertThat(label).isEqualTo("started");
  }
}
