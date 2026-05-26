<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>tate-yoko-pdf</title>
    <style>
        body { font-family: system-ui, sans-serif; max-width: 640px; margin: 2rem auto; padding: 0 1rem; line-height: 1.7; color: #222; }
        h1 { font-size: 1.5rem; margin-bottom: 0.5rem; }
        .lead { color: #555; margin-bottom: 1.5rem; }
        form { display: grid; gap: 1rem; padding: 1.25rem; border: 1px solid #ddd; border-radius: 8px; background: #fafafa; }
        label { display: grid; gap: 0.35rem; font-weight: 600; }
        input[type=file], select { font-size: 1rem; padding: 0.4rem; }
        .checkbox { display: flex; align-items: center; gap: 0.5rem; font-weight: 600; }
        .checkbox input { width: 1.1rem; height: 1.1rem; }
        button { font-size: 1rem; padding: 0.6rem 1.2rem; background: #2962ff; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
        button:hover { background: #1d4ed8; }
        .hint { color: #888; font-size: 0.85rem; }
    </style>
</head>
<body>
    <h1>tate-yoko-pdf</h1>
    <p class="lead">縦書きスキャンPDFを見開きレイアウトに変換します。</p>
    <form method="POST" action="/jobs" enctype="multipart/form-data">
        <label>
            PDFファイル
            <input type="file" name="pdf" accept="application/pdf" required>
        </label>
        <label>
            読み方向
            <select name="direction">
                <option value="RTL" selected>右綴じ (RTL) — 日本語の縦書きで一般的</option>
                <option value="LTR">左綴じ (LTR)</option>
            </select>
        </label>
        <label class="checkbox">
            <input type="checkbox" name="coverSingle" value="true">
            表紙を単独の見開きにする
        </label>
        <button type="submit">変換する</button>
        <p class="hint">PDFはローカルでのみ処理され、外部に送信されません。</p>
    </form>
    @template.keepalive()
</body>
</html>
