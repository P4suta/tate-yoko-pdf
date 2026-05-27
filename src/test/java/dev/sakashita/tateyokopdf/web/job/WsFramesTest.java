package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import org.junit.jupiter.api.Test;

final class WsFramesTest {

  @Test
  void startedFrameIncludesTotalAndTraceId() {
    String json = WsFrames.progress(new ProgressEvent.Started(7, "t-1"));
    assertThat(json)
        .contains("\"type\":\"started\"")
        .contains("\"total\":7")
        .contains("\"traceId\":\"t-1\"");
  }

  @Test
  void progressFrameIncludesCurrentTotalAndTraceId() {
    String json = WsFrames.progress(new ProgressEvent.Progress(3, 5, "t-2"));
    assertThat(json)
        .contains("\"current\":3")
        .contains("\"total\":5")
        .contains("\"traceId\":\"t-2\"");
  }

  @Test
  void completedFrameIncludesTraceId() {
    String json = WsFrames.progress(new ProgressEvent.Completed("t-3"));
    assertThat(json).contains("\"type\":\"completed\"").contains("\"traceId\":\"t-3\"");
  }

  @Test
  void failedFrameIncludesKindMessageAndTraceId() {
    String json =
        WsFrames.progress(new ProgressEvent.Failed(ErrorKind.PDF_CORRUPTED, "壊れた", "t-4"));
    assertThat(json)
        .contains("\"type\":\"failed\"")
        .contains("\"errorKind\":\"PDF_CORRUPTED\"")
        .contains("\"message\":\"壊れた\"")
        .contains("\"traceId\":\"t-4\"");
  }

  @Test
  void messageWithSpecialCharactersIsEscaped() {
    String json =
        WsFrames.progress(new ProgressEvent.Failed(ErrorKind.INTERNAL, "a\"b\\c\nd", "t"));
    assertThat(json).contains("a\\\"b\\\\c\\nd");
  }

  @Test
  void errorHelperBuildsFailedFrame() {
    String json = WsFrames.error(ErrorKind.JOB_NOT_FOUND, "missing", "tid");
    assertThat(json)
        .contains("\"errorKind\":\"JOB_NOT_FOUND\"")
        .contains("\"message\":\"missing\"")
        .contains("\"traceId\":\"tid\"");
  }
}
