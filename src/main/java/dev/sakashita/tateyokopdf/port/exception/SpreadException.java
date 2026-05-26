package dev.sakashita.tateyokopdf.port.exception;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class SpreadException extends RuntimeException {

  private final ErrorKind kind;
  private final String userMessage;
  private final @Nullable String technicalDetail;

  public SpreadException(
      ErrorKind kind,
      String userMessage,
      @Nullable String technicalDetail,
      @Nullable Throwable cause) {
    super(buildMessage(kind, userMessage, technicalDetail), cause);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.userMessage = Objects.requireNonNull(userMessage, "userMessage");
    this.technicalDetail = technicalDetail;
  }

  public static SpreadException of(ErrorKind kind) {
    return new SpreadException(kind, kind.defaultUserMessage(), null, null);
  }

  public static SpreadException of(ErrorKind kind, Throwable cause) {
    return new SpreadException(kind, kind.defaultUserMessage(), null, cause);
  }

  public static SpreadException withDetail(
      ErrorKind kind, String technicalDetail, @Nullable Throwable cause) {
    return new SpreadException(kind, kind.defaultUserMessage(), technicalDetail, cause);
  }

  public ErrorKind kind() {
    return kind;
  }

  public String userMessage() {
    return userMessage;
  }

  public @Nullable String technicalDetail() {
    return technicalDetail;
  }

  private static String buildMessage(
      ErrorKind kind, String userMessage, @Nullable String technicalDetail) {
    return technicalDetail == null
        ? "[" + kind + "] " + userMessage
        : "[" + kind + "] " + userMessage + " (" + technicalDetail + ")";
  }
}
