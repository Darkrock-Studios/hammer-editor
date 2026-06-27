// Vendor-agnostic event tracking. Forwards clicks on [data-track-event] elements to
// window.hammerTrack, which the active analytics provider defines. No-ops if undefined.
(function () {
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
