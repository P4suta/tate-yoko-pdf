<script lang="ts">
	import { browser } from '$app/environment';

	let { children } = $props();

	// Browser-presence WebSocket: when this socket closes (tab/window dies), the
	// server's IdleShutdown notices and exits. The `beforeunload` listener sends
	// an explicit close so the server doesn't have to wait for the TCP keepalive
	// to time out before noticing the tab is gone.
	$effect(() => {
		if (!browser) return;
		let ws: WebSocket | undefined;
		try {
			ws = new WebSocket(
				`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/keepalive`
			);
		} catch {
			// best-effort; non-critical if the keepalive cannot open
		}
		const closeWs = () => ws?.close();
		window.addEventListener('beforeunload', closeWs);
		return () => {
			window.removeEventListener('beforeunload', closeWs);
			closeWs();
		};
	});
</script>

{@render children()}
