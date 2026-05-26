@import dev.sakashita.tateyokopdf.web.job.Job
@param Job job

<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>変換中 — tate-yoko-pdf</title>
    <style>
        body { font-family: system-ui, sans-serif; max-width: 640px; margin: 2rem auto; padding: 0 1rem; line-height: 1.7; color: #222; }
        h1 { font-size: 1.5rem; }
        .status { color: #555; margin-top: 1rem; }
        .bar { width: 100%; height: 22px; background: #eee; border-radius: 6px; overflow: hidden; margin-top: 1rem; }
        .bar-fill { height: 100%; background: #2962ff; transition: width 0.2s ease; width: 0%; }
        .error { color: #c62828; }
    </style>
</head>
<body>
    <h1>変換中…</h1>
    <p class="status" id="status">準備中…</p>
    <div class="bar"><div class="bar-fill" id="bar"></div></div>
    <script>
        const jobId = "${job.id().toString()}";
        const wsUrl = (location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/jobs/" + jobId + "/ws";
        const ws = new WebSocket(wsUrl);
        const bar = document.getElementById("bar");
        const status = document.getElementById("status");
        ws.onmessage = (e) => {
            const msg = JSON.parse(e.data);
            if (msg.type === "started") {
                status.textContent = "0 / " + msg.total + " 見開き";
            } else if (msg.type === "progress") {
                const pct = msg.total > 0 ? (msg.current * 100 / msg.total) : 0;
                bar.style.width = pct + "%";
                status.textContent = msg.current + " / " + msg.total + " 見開き";
            } else if (msg.type === "completed") {
                bar.style.width = "100%";
                status.textContent = "完了。結果ページへ移動します…";
                window.location.href = "/jobs/" + jobId + "/result";
            } else if (msg.type === "failed") {
                status.textContent = "エラー: " + msg.message;
                status.classList.add("error");
            }
        };
        ws.onerror = () => {
            status.textContent = "サーバーとの接続に失敗しました。";
            status.classList.add("error");
        };
    </script>
    @template.keepalive()
</body>
</html>
