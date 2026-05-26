package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class ProgressEventTest {

  @Test
  void startedHoldsTotal() {
    var e = new ProgressEvent.Started(10);
    assertThat(e.total()).isEqualTo(10);
  }

  @Test
  void progressHoldsCounters() {
    var e = new ProgressEvent.Progress(3, 7);
    assertThat(e.current()).isEqualTo(3);
    assertThat(e.total()).isEqualTo(7);
  }

  @Test
  void completedIsInstantiable() {
    assertThat(new ProgressEvent.Completed()).isNotNull();
  }

  @Test
  void failedHoldsMessage() {
    var e = new ProgressEvent.Failed("oops");
    assertThat(e.message()).isEqualTo("oops");
  }

  @Test
  void sealedSwitchHandlesAllCases() {
    ProgressEvent ev = new ProgressEvent.Started(1);
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
