<script lang="ts">
	import { page } from '$app/state';
</script>

<h1>ページが見つかりません</h1>
<p class="message">{page.error?.message ?? `HTTP ${page.status}`}</p>
<a class="back" href="/">トップに戻る</a>

<style>
	h1 {
		font-size: 1.5rem;
		color: #c62828;
	}
	.message {
		padding: 1rem;
		background: #fff5f5;
		border-left: 4px solid #c62828;
		margin: 1.5rem 0;
		white-space: pre-wrap;
	}
	.back {
		font-size: 1rem;
		padding: 0.6rem 1.2rem;
		color: #2962ff;
		background: transparent;
		border: 1px solid #2962ff;
		border-radius: 6px;
		text-decoration: none;
	}
</style>
