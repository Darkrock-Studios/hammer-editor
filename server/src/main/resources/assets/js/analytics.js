// Vendor-agnostic event tracking. Bootstraps the configured provider, defines window.hammerTrack
// in that provider's terms, and forwards clicks on [data-track-event] elements to it.
//
// Provider settings arrive as data-* attributes on this script's own tag, because a CSP without
// 'unsafe-inline' leaves no way to hand a vendor bootstrap to the page as an inline script.
(function () {
	const config = document.getElementById('hammer-analytics');
	const provider = config && config.dataset.provider;

	if (provider === 'umami') {
		window.hammerTrack = function (name, data) {
			if (window.umami) window.umami.track(name, data);
		};
	} else if (provider === 'google') {
		// gtag.js reads window.dataLayer when it loads; `|| []` keeps whichever of us runs first.
		window.dataLayer = window.dataLayer || [];
		window.gtag = function () {
			window.dataLayer.push(arguments);
		};
		window.gtag('js', new Date());
		window.gtag('config', config.dataset.measurementId);
		window.hammerTrack = function (name, data) {
			if (window.gtag) window.gtag('event', name, data);
		};
	}

	document.addEventListener('click', function (e) {
		const el = e.target.closest('[data-track-event]');
		if (!el || typeof window.hammerTrack !== 'function') return;

		const data = {};
		for (const [key, value] of Object.entries(el.dataset)) {
			if (key.startsWith('track') && key !== 'trackEvent') {
				data[key.slice(5).toLowerCase()] = value;
			}
		}
		window.hammerTrack(el.dataset.trackEvent, data);
	});
})();
