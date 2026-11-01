/**
 * Monitoring charts. Each `.mon-chart` carries its series as JSON in data-chart, its legend
 * labels in data-label-N and, where it shows one, its no-data message in data-empty.
 */
(function () {
	var ChartCtor = (window.frappe && window.frappe.Chart) || window.Chart;
	if (!ChartCtor) return;

	function percent(v) {
		return v != null ? v.toFixed(1) + '%' : '';
	}

	function duration(v) {
		return v != null ? (v >= 1000 ? (v / 1000).toFixed(1) + 's' : v + 'ms') : '';
	}

	var CHARTS = {
		'mon-requests-chart': {
			series: ['requests', 'errors'], height: 240, colors: ['#d97706', '#ef4444'],
			minLabels: 1, showEmpty: true, hideDots: 0, regionFill: 0
		},
		'mon-active-users-chart': {
			series: ['sync', 'web'], height: 240, colors: ['#d97706', '#2563eb'],
			minLabels: 1, showEmpty: false, hideDots: 0, regionFill: 0
		},
		'mon-readers-chart': {
			series: ['readers'], height: 240, colors: ['#d97706'],
			minLabels: 1, showEmpty: false, hideDots: 0, regionFill: 0
		},
		'mon-error-rate-chart': {
			series: ['errorRates'], height: 120, colors: ['#ef4444'],
			minLabels: 2, showEmpty: true, hideDots: 1, regionFill: 1, format: percent
		},
		'mon-latency-chart': {
			series: ['p95Ms'], height: 120, colors: ['#d97706'],
			minLabels: 2, showEmpty: true, hideDots: 1, regionFill: 1, format: duration
		}
	};

	Object.keys(CHARTS).forEach(function (id) {
		var el = document.getElementById(id);
		if (!el) return;
		var cfg = CHARTS[id];
		var data = JSON.parse(el.dataset.chart);

		if (!data.labels || data.labels.length < cfg.minLabels) {
			if (cfg.showEmpty) {
				var p = document.createElement('p');
				p.className = 'mon-chart__empty';
				p.textContent = el.dataset.empty;
				el.replaceChildren(p);
			}
			return;
		}

		var options = {
			data: {
				labels: data.labels,
				datasets: cfg.series.map(function (key, i) {
					return { name: el.dataset['label' + i], values: data[key] };
				})
			},
			type: 'line',
			height: cfg.height,
			colors: cfg.colors,
			axisOptions: { xAxisMode: 'tick', xIsSeries: true },
			lineOptions: { hideDots: cfg.hideDots, regionFill: cfg.regionFill }
		};
		if (cfg.format) options.tooltipOptions = { formatTooltipY: cfg.format };
		new ChartCtor(el, options);
	});
})();
