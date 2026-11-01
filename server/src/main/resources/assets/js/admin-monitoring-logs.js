function exportLogs() {
	var params = new URLSearchParams();
	var level = document.getElementById('logLevel').value;
	var q = document.getElementById('logSearch').value;
	if (level) params.set('level', level);
	if (q) params.set('q', q);
	var qs = params.toString();
	window.location.href = '/admin/monitoring/logs/export' + (qs ? '?' + qs : '');
}

// Polls only while auto-refresh is ticked; "refresh" is the tail's own hx-trigger.
setInterval(function () {
	if (document.getElementById('logAutoRefresh').checked) {
		htmx.trigger('#logTail', 'refresh');
	}
}, 3000);
