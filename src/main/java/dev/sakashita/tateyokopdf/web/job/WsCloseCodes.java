package dev.sakashita.tateyokopdf.web.job;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;

/**
 * Application-defined WebSocket close codes (RFC 6455 §7.4.2 reserves the 4000-4999 range for
 * private use). 1008 (policy violation) is intentionally avoided because clients should be able to
 * distinguish "job missing" from "validation problem".
 */
public final class WsCloseCodes {

  public static final int NORMAL = 1000;
  public static final int APP_BASE = 4000;
  public static final int JOB_FAILED = 4001;
  public static final int JOB_NOT_FOUND = 4404;
  public static final int JOB_EXPIRED = 4410;
  public static final int INTERNAL = 4500;

  private WsCloseCodes() {}

  public static int forErrorKind(ErrorKind kind) {
    return switch (kind) {
      case JOB_NOT_FOUND, PDF_NOT_FOUND -> JOB_NOT_FOUND;
      case JOB_EXPIRED, JOB_OUTPUT_GONE -> JOB_EXPIRED;
      case INTERNAL, OUT_OF_MEMORY -> INTERNAL;
      default -> JOB_FAILED;
    };
  }
}
