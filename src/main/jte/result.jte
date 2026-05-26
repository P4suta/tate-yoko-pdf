@import dev.sakashita.tateyokopdf.web.job.Job
@param Job job

<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>変換完了 — tate-yoko-pdf</title>
    <style>
        body { font-family: system-ui, sans-serif; max-width: 640px; margin: 2rem auto; padding: 0 1rem; line-height: 1.7; color: #222; }
        h1 { font-size: 1.5rem; }
        .filename { color: #555; }
        .actions { display: flex; gap: 1rem; margin-top: 1.5rem; flex-wrap: wrap; }
        .download { font-size: 1rem; padding: 0.6rem 1.2rem; background: #2962ff; color: #fff; border: none; border-radius: 6px; text-decoration: none; }
        .download:hover { background: #1d4ed8; }
        .secondary { font-size: 1rem; padding: 0.6rem 1.2rem; color: #2962ff; background: transparent; border: 1px solid #2962ff; border-radius: 6px; text-decoration: none; }
        .secondary:hover { background: #eef2ff; }
    </style>
</head>
<body>
    <h1>変換が完了しました</h1>
    <p class="filename">ファイル: ${job.originalName()}</p>
    <div class="actions">
        <a class="download" href="/jobs/${job.id().toString()}/download">PDFをダウンロード</a>
        <a class="secondary" href="/">別のPDFを変換</a>
    </div>
</body>
</html>
