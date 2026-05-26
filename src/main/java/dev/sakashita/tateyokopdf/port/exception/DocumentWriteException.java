package dev.sakashita.tateyokopdf.port.exception;

public class DocumentWriteException extends SpreadException {
  public DocumentWriteException(String message, Throwable cause) {
    super(message, cause);
  }
}
