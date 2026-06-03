package dev.sakashita.tateyokopdf.domain.exception;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The single exception type the conversion raises, tagging every failure with an {@link ErrorKind}
 * plus a user-facing message and an optional technical detail.
 *
 * <p>Unchecked: it propagates to the CLI boundary, where {@code ExceptionMapper} maps the kind to
 * an exit code and log level. Prefer the {@link #of} / {@link #withDetail} factories over the
 * constructor.
 */
public final class SpreadException extends RuntimeException {

  private final ErrorKind kind;
  private final String userMessage;
  private final @Nullable String technicalDetail;

  /**
   * @param kind the failure category (non-null)
   * @param userMessage the message to show the user (non-null)
   * @param technicalDetail an optional diagnostic detail kept out of the user message
   * @param cause the underlying cause, if any
   */
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

  /** {@return an exception of {@code kind} with its default user message and no cause} */
  public static SpreadException of(ErrorKind kind) {
    return new SpreadException(kind, kind.defaultUserMessage(), null, null);
  }

  /**
   * {@return an exception of {@code kind} with its default user message, wrapping {@code cause}}
   */
  public static SpreadException of(ErrorKind kind, Throwable cause) {
    return new SpreadException(kind, kind.defaultUserMessage(), null, cause);
  }

  /**
   * {@return an exception of {@code kind} with its default user message plus a diagnostic {@code
   * technicalDetail}, optionally wrapping {@code cause}}
   */
  public static SpreadException withDetail(
      ErrorKind kind, String technicalDetail, @Nullable Throwable cause) {
    return new SpreadException(kind, kind.defaultUserMessage(), technicalDetail, cause);
  }

  /** {@return the failure category} */
  public ErrorKind kind() {
    return kind;
  }

  /** {@return the user-facing message} */
  public String userMessage() {
    return userMessage;
  }

  /** {@return the optional diagnostic detail, or {@code null} if none} */
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
