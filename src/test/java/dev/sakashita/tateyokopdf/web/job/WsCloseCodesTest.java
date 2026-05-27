package dev.sakashita.tateyokopdf.web.job;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import org.junit.jupiter.api.Test;

final class WsCloseCodesTest {

  @Test
  void jobNotFoundMapsTo4404() {
    assertThat(WsCloseCodes.forErrorKind(ErrorKind.JOB_NOT_FOUND))
        .isEqualTo(WsCloseCodes.JOB_NOT_FOUND);
  }

  @Test
  void pdfNotFoundMapsTo4404() {
    assertThat(WsCloseCodes.forErrorKind(ErrorKind.PDF_NOT_FOUND))
        .isEqualTo(WsCloseCodes.JOB_NOT_FOUND);
  }

  @Test
  void jobExpiredMapsTo4410() {
    assertThat(WsCloseCodes.forErrorKind(ErrorKind.JOB_EXPIRED))
        .isEqualTo(WsCloseCodes.JOB_EXPIRED);
  }

  @Test
  void internalMapsTo4500() {
    assertThat(WsCloseCodes.forErrorKind(ErrorKind.INTERNAL)).isEqualTo(WsCloseCodes.INTERNAL);
  }

  @Test
  void oomMapsTo4500() {
    assertThat(WsCloseCodes.forErrorKind(ErrorKind.OUT_OF_MEMORY)).isEqualTo(WsCloseCodes.INTERNAL);
  }

  @Test
  void otherKindsDefaultToJobFailed() {
    assertThat(WsCloseCodes.forErrorKind(ErrorKind.PDF_CORRUPTED))
        .isEqualTo(WsCloseCodes.JOB_FAILED);
    assertThat(WsCloseCodes.forErrorKind(ErrorKind.UPLOAD_INVALID))
        .isEqualTo(WsCloseCodes.JOB_FAILED);
  }

  @Test
  void closeCodesAreInTheAppRange() {
    assertThat(WsCloseCodes.JOB_FAILED).isGreaterThanOrEqualTo(WsCloseCodes.APP_BASE);
    assertThat(WsCloseCodes.JOB_NOT_FOUND).isGreaterThanOrEqualTo(WsCloseCodes.APP_BASE);
    assertThat(WsCloseCodes.JOB_EXPIRED).isGreaterThanOrEqualTo(WsCloseCodes.APP_BASE);
    assertThat(WsCloseCodes.INTERNAL).isGreaterThanOrEqualTo(WsCloseCodes.APP_BASE);
  }
}
