<script lang="ts">
	import { browser } from '$app/environment';

	let { children } = $props();

	// Browser-presence WebSocket: when this socket closes (tab/window dies), the
	// server's IdleShutdown notices and exits. Mirrors the previous keepalive.jte.
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
		return () => ws?.close();
	});
</script>

{@render children()}
