@param String kind
@param String message
@param String traceId

<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>エラー — tate-yoko-pdf</title>
    <style>
        body { font-family: system-ui, sans-serif; max-width: 640px; margin: 2rem auto; padding: 0 1rem; line-height: 1.7; color: #222; }
        h1 { font-size: 1.5rem; color: #c62828; }
        .kind { display: inline-block; padding: 0.15rem 0.5rem; border-radius: 4px; background: #fde7e7; color: #c62828; font-size: 0.85rem; font-weight: 600; letter-spacing: 0.02em; }
        .message { padding: 1rem; background: #fff5f5; border-left: 4px solid #c62828; margin: 1.5rem 0; white-space: pre-wrap; }
        .trace { margin-top: 1.5rem; font-size: 0.85rem; color: #777; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
        .back { font-size: 1rem; padding: 0.6rem 1.2rem; color: #2962ff; background: transparent; border: 1px solid #2962ff; border-radius: 6px; text-decoration: none; }
    </style>
</head>
<body>
    <h1>変換に失敗しました <span class="kind">${kind}</span></h1>
    <p class="message">${message}</p>
    <a class="back" href="/">戻ってやり直す</a>
    <p class="trace">サポート連絡時にこの ID をお伝えください: <code>${traceId}</code></p>
    @template.keepalive()
</body>
</html>
