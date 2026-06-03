package dev.sakashita.tateyokopdf.domain.exception;

/**
 * The taxonomy of failures the conversion can surface. Each kind carries a default Japanese
 * user-facing message and a {@code clientFault} flag (true = caused by the input or usage, false =
 * internal or environmental), which {@code ExceptionMapper} turns into a process exit code and log
 * level. README's troubleshooting table is keyed by these names.
 */
public enum ErrorKind {
  PDF_CORRUPTED("PDFを読み込めませんでした。ファイルが破損している可能性があります。", true),
  PDF_PASSWORD_PROTECTED("PDFがパスワードで保護されているため処理できません。", true),
  PDF_NOT_FOUND("指定された PDF ファイルが見つかりません。", true),
  PDF_INVALID_PAGE("PDFのページ指定が不正です。", true),
  PDF_WRITE_FAILED("出力 PDF の書き出しに失敗しました。", false),
  INVALID_PARAMETER("入力値が不正です。", true),
  OUT_OF_MEMORY("メモリが不足しました。-Xmx を増やすか、ページ数の少ない PDF で試してください。", false),
  INTERNAL("予期しないエラーが発生しました。", false);

  private final String defaultUserMessage;
  private final boolean clientFault;

  ErrorKind(String defaultUserMessage, boolean clientFault) {
    this.defaultUserMessage = defaultUserMessage;
    this.clientFault = clientFault;
  }

  /** {@return the default Japanese message shown to the user for this kind} */
  public String defaultUserMessage() {
    return defaultUserMessage;
  }

  /**
   * {@return whether this failure is the caller's fault (bad input or usage) rather than internal}
   */
  public boolean isClientFault() {
    return clientFault;
  }
}
