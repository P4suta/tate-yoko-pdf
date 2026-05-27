package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import org.junit.jupiter.api.Test;

/**
 * Pins the WebSocket wire format. The frontend {@code types.ts} mirrors this shape exactly; any
 * field rename here will break the SvelteKit client, so the assertions intentionally check both
 * field names and their content rather than only deserializing.
 */
final class JobJsonMappingTest {

  @Test
  void startedFrameIncludesTotalAndTraceId() {
    String json = JobJsonMapping.toJson(new ProgressEvent.Started(7, "t-1"));
    assertThat(json)
        .contains("\"type\":\"started\"")
        .contains("\"total\":7")
        .contains("\"traceId\":\"t-1\"");
  }

  @Test
  void progressFrameIncludesCurrentTotalAndTraceId() {
    String json = JobJsonMapping.toJson(new ProgressEvent.Progress(3, 5, "t-2"));
    assertThat(json)
        .contains("\"type\":\"progress\"")
        .contains("\"current\":3")
        .contains("\"total\":5")
        .contains("\"traceId\":\"t-2\"");
  }

  @Test
  void completedFrameIncludesTraceId() {
    String json = JobJsonMapping.toJson(new ProgressEvent.Completed("t-3"));
    assertThat(json).contains("\"type\":\"completed\"").contains("\"traceId\":\"t-3\"");
  }

  @Test
  void failedFrameIncludesKindMessageAndTraceId() {
    String json =
        JobJsonMapping.toJson(new ProgressEvent.Failed(ErrorKind.PDF_CORRUPTED, "壊れた", "t-4"));
    assertThat(json)
        .contains("\"type\":\"failed\"")
        .contains("\"errorKind\":\"PDF_CORRUPTED\"")
        .contains("\"message\":\"壊れた\"")
        .contains("\"traceId\":\"t-4\"");
  }

  @Test
  void messageWithSpecialCharactersIsEscaped() {
    String json =
        JobJsonMapping.toJson(new ProgressEvent.Failed(ErrorKind.INTERNAL, "a\"b\\c\nd", "t"));
    assertThat(json).contains("a\\\"b\\\\c\\nd");
  }

  @Test
  void failedFrameHelperBuildsSameShapeAsToJson() {
    String json = JobJsonMapping.failedFrame(ErrorKind.JOB_NOT_FOUND, "missing", "tid");
    assertThat(json)
        .contains("\"type\":\"failed\"")
        .contains("\"errorKind\":\"JOB_NOT_FOUND\"")
        .contains("\"message\":\"missing\"")
        .contains("\"traceId\":\"tid\"");
  }
}
