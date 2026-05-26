package dev.sakashita.tateyokopdf.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

final class TraceContextTest {

  @AfterEach
  void cleanup() {
    TraceContext.clear();
  }

  @Test
  void newTraceIdIsHex32() {
    String id = TraceContext.newTraceId();
    assertThat(id).hasSize(32).matches("^[0-9a-f]{32}$");
  }

  @Test
  void newTraceIdsAreUnique() {
    String a = TraceContext.newTraceId();
    String b = TraceContext.newTraceId();
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void putTraceIdSetsMdcAndCurrentTraceIdReturnsIt() {
    TraceContext.putTraceId("abc123");
    assertThat(TraceContext.currentTraceId()).isEqualTo("abc123");
    assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isEqualTo("abc123");
  }

  @Test
  void putJobIdSetsMdcAndNullRemoves() {
    TraceContext.putJobId("job-1");
    assertThat(MDC.get(TraceContext.MDC_JOB_ID)).isEqualTo("job-1");
    TraceContext.putJobId(null);
    assertThat(MDC.get(TraceContext.MDC_JOB_ID)).isNull();
  }

  @Test
  void clearRemovesBothKeys() {
    TraceContext.putTraceId("t");
    TraceContext.putJobId("j");
    TraceContext.clear();
    assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isNull();
    assertThat(MDC.get(TraceContext.MDC_JOB_ID)).isNull();
  }
}
