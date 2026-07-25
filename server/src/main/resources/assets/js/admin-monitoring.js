// Charts and table interactions for the admin monitoring pages. One file for all of them: each
// block no-ops when its element is absent, so a page only pays for what it renders.
//
// Series data and localized labels arrive as <script type="application/json"> islands rather than
// interpolated into a script body, which the CSP has no 'unsafe-inline' to allow.
(function () {
	const ChartCtor = (window.frappe && window.frappe.Chart) || window.Chart;

	function island(id) {
		const el = document.getElementById(id);
		return el ? JSON.parse(el.textContent) : null;
	}

	const LABELS = island('mon-labels') || {};

	/**
	 * Renders a line chart, or the empty-state message when there aren't enough points.
	 * `datasets` maps each series name to the key holding its values in the island.
	 */
	function lineChart(options) {
		const el = document.getElementById(options.element);
		if (!el) return;

		const data = island(options.data);
		const enough = data && data.labels && data.labels.length >= (options.minPoints || 1);
		if (!enough) {
			if (options.showEmpty) {
				el.innerHTML = '<p class="mon-chart__empty"></p>';
				el.firstChild.textContent = LABELS.noData || '';
			}
			return;
		}
		if (!ChartCtor) return;

		new ChartCtor(el, {
			data: {
				labels: data.labels,
				datasets: options.series.map(function (series) {
					return {name: LABELS[series.label] || '', values: data[series.values]};
				})
			},
			type: 'line',
			height: options.height,
			colors: options.colors,
			axisOptions: {xAxisMode: 'tick', xIsSeries: true},
			lineOptions: options.lineOptions || {hideDots: 0, regionFill: 0},
			tooltipOptions: options.tooltipOptions
		});
	}

	// --- Overview page ---

	lineChart({
		element: 'mon-requests-chart',
		data: 'mon-requests-data',
		series: [{label: 'requests', values: 'requests'}, {label: 'errors', values: 'errors'}],
		colors: ['#d97706', '#ef4444'],
		height: 240,
		showEmpty: true
	});

	lineChart({
		element: 'mon-active-users-chart',
		data: 'mon-active-users-data',
		series: [{label: 'activeUsersSync', values: 'sync'}, {label: 'activeUsersWeb', values: 'web'}],
		colors: ['#d97706', '#2563eb'],
		height: 240
	});

	lineChart({
		element: 'mon-readers-chart',
		data: 'mon-readers-data',
		series: [{label: 'readersUnique', values: 'readers'}],
		colors: ['#d97706'],
		height: 240
	});

	// --- Errors page ---

	lineChart({
		element: 'mon-error-rate-chart',
		data: 'mon-error-rate-data',
		series: [{label: 'errorRate', values: 'errorRates'}],
		colors: ['#ef4444'],
		height: 120,
		minPoints: 2,
		showEmpty: true,
		lineOptions: {hideDots: 1, regionFill: 1},
		tooltipOptions: {
			formatTooltipY: function (v) {
				return v != null ? v.toFixed(1) + '%' : '';
			}
		}
	});

	// Delegated so it also covers rows swapped in by HTMX (ignore/unignore refresh).
	document.addEventListener('click', function (event) {
		const btn = event.target.closest('[data-copy-stack]');
		if (!btn) return;

		const wrap = btn.closest('.mon-error__stack-wrap');
		const pre = wrap && wrap.querySelector('.mon-error__stack');
		if (!pre) return;

		const original = btn.innerHTML;
		const showCopied = function () {
			btn.classList.add('copied');
			btn.innerHTML = '<i class="fa-solid fa-check"></i> ' + btn.dataset.copiedLabel;
			setTimeout(function () {
				btn.classList.remove('copied');
				btn.innerHTML = original;
			}, 2000);
		};

		// navigator.clipboard is undefined on insecure (plain HTTP) self-hosted origins.
		const fallback = function () {
			const range = document.createRange();
			range.selectNodeContents(pre);
			const sel = window.getSelection();
			sel.removeAllRanges();
			sel.addRange(range);
			try {
				document.execCommand('copy');
				showCopied();
			} catch (err) { /* nothing to do */ }
			sel.removeAllRanges();
		};

		if (navigator.clipboard && navigator.clipboard.writeText) {
			navigator.clipboard.writeText(pre.textContent).then(showCopied, fallback);
		} else {
			fallback();
		}
	});

	// --- Performance page ---

	lineChart({
		element: 'mon-latency-chart',
		data: 'mon-latency-data',
		series: [{label: 'latency', values: 'p95Ms'}],
		colors: ['#d97706'],
		height: 120,
		minPoints: 2,
		showEmpty: true,
		lineOptions: {hideDots: 1, regionFill: 1},
		tooltipOptions: {
			formatTooltipY: function (v) {
				if (v == null) return '';
				return v >= 1000 ? (v / 1000).toFixed(1) + 's' : v + 'ms';
			}
		}
	});

	(function endpointsTableSort() {
		const table = document.getElementById('endpointsTable');
		if (!table) return;

		const tbody = table.querySelector('tbody');
		const headers = Array.from(table.querySelectorAll('th[data-col]'));
		let sortCol = -1;
		let sortAsc = true;

		headers.forEach(function (th) {
			th.addEventListener('click', function () {
				const col = parseInt(th.getAttribute('data-col'));
				const isNum = th.getAttribute('data-type') === 'num';
				if (sortCol === col) {
					sortAsc = !sortAsc;
				} else {
					sortCol = col;
					sortAsc = !isNum; // numeric → desc first; string → asc first
				}
				headers.forEach(function (h) {
					h.classList.remove('mon-sort-asc', 'mon-sort-desc');
				});
				th.classList.add(sortAsc ? 'mon-sort-asc' : 'mon-sort-desc');

				const rows = Array.from(tbody.querySelectorAll('tr'));
				rows.sort(function (a, b) {
					const aVal = (a.querySelectorAll('td')[col] || {}).getAttribute('data-val') || '';
					const bVal = (b.querySelectorAll('td')[col] || {}).getAttribute('data-val') || '';
					const cmp = isNum ? parseFloat(aVal) - parseFloat(bVal) : aVal.localeCompare(bVal);
					return sortAsc ? cmp : -cmp;
				});
				rows.forEach(function (r) {
					tbody.appendChild(r);
				});
			});
		});
	})();
})();
