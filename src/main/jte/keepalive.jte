!{/* Browser-presence WebSocket — when this socket closes (tab/window dies), the server's IdleShutdown notices and exits. */}
<script>
(function() {
    try {
        new WebSocket((location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/ws/keepalive");
    } catch (e) { /* ignore */ }
})();
</script>
