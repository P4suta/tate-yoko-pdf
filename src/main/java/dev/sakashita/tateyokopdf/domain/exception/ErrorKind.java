package dev.sakashita.tateyokopdf.domain.exception;

public enum ErrorKind {
  PDF_CORRUPTED("PDFを読み込めませんでした。ファイルが破損している可能性があります。", true),
  PDF_PASSWORD_PROTECTED("PDFがパスワードで保護されているため処理できません。", true),
  PDF_TOO_LARGE("PDFが大きすぎます。500MB 以下のファイルを指定してください。", true),
  PDF_NOT_FOUND("指定された PDF ファイルが見つかりません。", true),
  PDF_INVALID_PAGE("PDFのページ指定が不正です。", true),
  PDF_WRITE_FAILED("出力 PDF の書き出しに失敗しました。", false),
  UPLOAD_INVALID("アップロードされたファイルが PDF として正しく認識できません。", true),
  UPLOAD_EMPTY("PDF ファイルを選択してください。", true),
  JOB_NOT_FOUND("指定されたジョブが見つかりません。", true),
  JOB_EXPIRED("ジョブの保存期間が切れました。最初からやり直してください。", true),
  JOB_OUTPUT_GONE("出力ファイルが既に削除されています。最初からやり直してください。", true),
  INVALID_PARAMETER("入力値が不正です。", true),
  OUT_OF_MEMORY("メモリが不足しました。-Xmx を増やすか、ページ数の少ない PDF で試してください。", false),
  INTERNAL("予期しないエラーが発生しました。", false);

  private final String defaultUserMessage;
  private final boolean clientFault;

  ErrorKind(String defaultUserMessage, boolean clientFault) {
    this.defaultUserMessage = defaultUserMessage;
    this.clientFault = clientFault;
  }

  public String defaultUserMessage() {
    return defaultUserMessage;
  }

  public boolean isClientFault() {
    return clientFault;
  }
}
