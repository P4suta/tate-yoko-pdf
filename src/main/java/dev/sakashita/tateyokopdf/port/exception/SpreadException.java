package dev.sakashita.tateyokopdf.port.exception;

public class SpreadException extends RuntimeException {
  public SpreadException(String message) {
    super(message);
  }

  public SpreadException(String message, Throwable cause) {
    super(message, cause);
  }
}
