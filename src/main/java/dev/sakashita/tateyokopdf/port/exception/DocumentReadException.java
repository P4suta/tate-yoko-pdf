package dev.sakashita.tateyokopdf.port.exception;

public class DocumentReadException extends SpreadException {
  public DocumentReadException(String message, Throwable cause) {
    super(message, cause);
  }
}
