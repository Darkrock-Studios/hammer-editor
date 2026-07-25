// Admin monitoring logs page: the export button, and the auto-refresh poll.
/* global htmx, hammerActions */
(function () {
	// Drives the log tail's `refreshLogs` trigger. htmx can express this as a bracketed filter on
	// `every 3s`, but it compiles those with new Function(), which the CSP has no 'unsafe-eval' for.
	setInterval(function () {
		const autoRefresh = document.getElementById('logAutoRefresh');
		if (autoRefresh && autoRefresh.checked) {
			htmx.trigger('#logTail', 'refreshLogs');
		}
	}, 3000);

	hammerActions({
		'logs-export': function () {
			const params = new URLSearchParams();
			const level = document.getElementById('logLevel').value;
			const query = document.getElementById('logSearch').value;
			if (level) params.set('level', level);
			if (query) params.set('q', query);

			const qs = params.toString();
			window.location.href = '/admin/monitoring/logs/export' + (qs ? '?' + qs : '');
		},

		// The controls only drive client-side filtering; a native submit would reload the page.
		'logs-controls-submit': () => false
	});
})();
