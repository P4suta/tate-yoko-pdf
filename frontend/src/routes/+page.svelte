<script lang="ts">
	import { goto } from '$app/navigation';
	import type { ReadingDirection, SubmitResponse } from '$lib/types';

	let fileInput: HTMLInputElement | undefined = $state();
	let direction: ReadingDirection = $state('RTL');
	let coverSingle = $state(false);
	let submitting = $state(false);
	let error: string | null = $state(null);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		const file = fileInput?.files?.[0];
		if (!file) {
			error = 'PDF ファイルを選択してください。';
			return;
		}
		submitting = true;
		error = null;
		try {
			const form = new FormData();
			form.append('pdf', file);
			form.append('direction', direction);
			if (coverSingle) form.append('coverSingle', 'true');
			const res = await fetch('/api/jobs', { method: 'POST', body: form });
			if (!res.ok) {
				const body = (await res.json().catch(() => null)) as { message?: string } | null;
				throw new Error(body?.message ?? `アップロードに失敗しました (HTTP ${res.status})`);
			}
			const { id } = (await res.json()) as SubmitResponse;
			await goto(`/jobs/${id}`);
		} catch (e) {
			error = e instanceof Error ? e.message : String(e);
		} finally {
			submitting = false;
		}
	}
</script>

<h1>tate-yoko-pdf</h1>
<p class="lead">縦書きスキャン PDF を見開きレイアウトに変換します。</p>

<form onsubmit={submit}>
	<label>
		PDF ファイル
		<input bind:this={fileInput} type="file" accept="application/pdf" required />
	</label>

	<label>
		読み方向
		<select bind:value={direction}>
			<option value="RTL">右綴じ (RTL) — 日本語の縦書きで一般的</option>
			<option value="LTR">左綴じ (LTR)</option>
		</select>
	</label>

	<label class="checkbox">
		<input type="checkbox" bind:checked={coverSingle} />
		表紙を単独の見開きにする
	</label>

	<button type="submit" disabled={submitting}>
		{submitting ? 'アップロード中…' : '変換する'}
	</button>

	{#if error}
		<p class="error">{error}</p>
	{/if}

	<p class="hint">PDF はローカルでのみ処理され、外部に送信されません。</p>
</form>

<style>
	h1 {
		font-size: 1.5rem;
		margin-bottom: 0.5rem;
	}
	.lead {
		color: #555;
		margin-bottom: 1.5rem;
	}
	form {
		display: grid;
		gap: 1rem;
		padding: 1.25rem;
		border: 1px solid #ddd;
		border-radius: 8px;
		background: #fafafa;
	}
	label {
		display: grid;
		gap: 0.35rem;
		font-weight: 600;
	}
	input[type='file'],
	select {
		font-size: 1rem;
		padding: 0.4rem;
	}
	.checkbox {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		font-weight: 600;
	}
	.checkbox input {
		width: 1.1rem;
		height: 1.1rem;
	}
	button {
		font-size: 1rem;
		padding: 0.6rem 1.2rem;
		background: #2962ff;
		color: #fff;
		border: none;
		border-radius: 6px;
		cursor: pointer;
	}
	button:hover:not(:disabled) {
		background: #1d4ed8;
	}
	button:disabled {
		opacity: 0.6;
		cursor: progress;
	}
	.hint {
		color: #888;
		font-size: 0.85rem;
	}
	.error {
		color: #c62828;
		font-weight: 600;
	}
</style>
