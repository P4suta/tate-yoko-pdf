<script lang="ts">
	import { page } from '$app/state';
	import type { ProgressEvent } from '$lib/types';

	type Status = 'connecting' | 'running' | 'completed' | 'failed';

	let status: Status = $state('connecting');
	let current = $state(0);
	let total = $state(0);
	let errorMsg: string | null = $state(null);
	let errorKind: string | null = $state(null);
	let traceId: string = $state('-');

	const jobId = $derived(page.params.id);
	const percent = $derived(total > 0 ? Math.min(100, Math.round((current * 100) / total)) : 0);
	const downloadUrl = $derived(`/api/jobs/${jobId}/download`);

	$effect(() => {
		if (!jobId) return;
		const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/jobs/${jobId}`;
		const ws = new WebSocket(wsUrl);
		ws.onmessage = (e: MessageEvent<string>) => {
			const msg = JSON.parse(e.data) as ProgressEvent;
			traceId = msg.traceId;
			switch (msg.type) {
				case 'started':
					status = 'running';
					total = msg.total;
					break;
				case 'progress':
					current = msg.current;
					total = msg.total;
					break;
				case 'completed':
					status = 'completed';
					current = total;
					break;
				case 'failed':
					status = 'failed';
					errorKind = msg.errorKind;
					errorMsg = msg.message;
					break;
			}
		};
		ws.onerror = () => {
			if (status !== 'completed' && status !== 'failed') {
				status = 'failed';
				errorMsg = 'サーバーとの接続に失敗しました。';
			}
		};
		return () => ws.close();
	});
</script>

{#if status === 'failed'}
	<h1 class="failed">
		変換に失敗しました
		{#if errorKind}<span class="kind">{errorKind}</span>{/if}
	</h1>
	<p class="message">{errorMsg ?? '不明なエラーが発生しました。'}</p>
	<a class="back" href="/">戻ってやり直す</a>
	<p class="trace">サポート連絡時にこの ID をお伝えください: <code>{traceId}</code></p>
{:else if status === 'completed'}
	<h1>変換が完了しました</h1>
	<div class="actions">
		<a class="download" href={downloadUrl}>PDF をダウンロード</a>
		<a class="secondary" href="/">別の PDF を変換</a>
	</div>
{:else}
	<h1>変換中…</h1>
	<p class="status">
		{#if status === 'connecting'}
			準備中…
		{:else}
			{current} / {total} 見開き
		{/if}
	</p>
	<div class="bar"><div class="bar-fill" style="width: {percent}%"></div></div>
{/if}

<style>
	h1 {
		font-size: 1.5rem;
	}
	h1.failed {
		color: #c62828;
	}
	.kind {
		display: inline-block;
		padding: 0.15rem 0.5rem;
		border-radius: 4px;
		background: #fde7e7;
		color: #c62828;
		font-size: 0.85rem;
		font-weight: 600;
		letter-spacing: 0.02em;
		margin-left: 0.5rem;
	}
	.message {
		padding: 1rem;
		background: #fff5f5;
		border-left: 4px solid #c62828;
		margin: 1.5rem 0;
		white-space: pre-wrap;
	}
	.status {
		color: #555;
		margin-top: 1rem;
	}
	.bar {
		width: 100%;
		height: 22px;
		background: #eee;
		border-radius: 6px;
		overflow: hidden;
		margin-top: 1rem;
	}
	.bar-fill {
		height: 100%;
		background: #2962ff;
		transition: width 0.2s ease;
	}
	.actions {
		display: flex;
		gap: 1rem;
		margin-top: 1.5rem;
		flex-wrap: wrap;
	}
	.download {
		font-size: 1rem;
		padding: 0.6rem 1.2rem;
		background: #2962ff;
		color: #fff;
		border: none;
		border-radius: 6px;
		text-decoration: none;
	}
	.download:hover {
		background: #1d4ed8;
	}
	.secondary,
	.back {
		font-size: 1rem;
		padding: 0.6rem 1.2rem;
		color: #2962ff;
		background: transparent;
		border: 1px solid #2962ff;
		border-radius: 6px;
		text-decoration: none;
	}
	.secondary:hover,
	.back:hover {
		background: #eef2ff;
	}
	.trace {
		margin-top: 1.5rem;
		font-size: 0.85rem;
		color: #777;
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
	}
</style>
