package dev.sakashita.tateyokopdf.port.exception;

public class PasswordProtectedException extends SpreadException {
  public PasswordProtectedException(String message, Throwable cause) {
    super(message, cause);
  }
}
