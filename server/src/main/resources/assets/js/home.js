// Download format selectors for Windows, macOS, and Linux
(function () {
	function setupDownloadSelector(selectId, btnId, iconId) {
		const select = document.getElementById(selectId);
		const btn = document.getElementById(btnId);
		const icon = document.getElementById(iconId);

		if (select && btn && icon) {
			select.addEventListener('change', function () {
				const selected = this.options[this.selectedIndex];
				btn.href = selected.dataset.url;
				icon.className = selected.dataset.icon;
				if (btn.dataset.trackEvent) {
					btn.dataset.trackFormat = selected.value;
				}
			});
		}
	}

	setupDownloadSelector('windows-format-select', 'windows-download-btn', 'windows-download-icon');
	setupDownloadSelector('macos-format-select', 'macos-download-btn', 'macos-download-icon');
	setupDownloadSelector('linux-format-select', 'linux-download-btn', 'linux-download-icon');
	setupDownloadSelector('android-format-select', 'android-download-btn', 'android-download-icon');
})();

// Feature tabs. Each tab's hash (#world, #share, ...) opens it directly.
(function () {
	const tabs = Array.from(document.querySelectorAll('.tab'));
	if (!tabs.length) return;

	function select(tab, focus) {
		tabs.forEach(function (t) {
			const on = t === tab;
			t.setAttribute('aria-selected', on ? 'true' : 'false');
			t.tabIndex = on ? 0 : -1;
			document.getElementById(t.getAttribute('aria-controls')).hidden = !on;
		});
		if (focus) tab.focus();
		tab.scrollIntoView({block: 'nearest', inline: 'nearest'});
	}

	tabs.forEach(function (tab, i) {
		tab.addEventListener('click', function () {
			select(tab, false);
			history.replaceState(null, '', '#' + tab.id.replace('tab-', ''));
		});
		tab.addEventListener('keydown', function (e) {
			if (e.key === 'ArrowRight') select(tabs[(i + 1) % tabs.length], true);
			if (e.key === 'ArrowLeft') select(tabs[(i + tabs.length - 1) % tabs.length], true);
			if (e.key === 'Home') { e.preventDefault(); select(tabs[0], true); }
			if (e.key === 'End') { e.preventDefault(); select(tabs[tabs.length - 1], true); }
		});
	});

	const fromHash = location.hash.length > 1 && document.getElementById('tab-' + location.hash.slice(1));
	if (fromHash && tabs.indexOf(fromHash) !== -1) select(fromHash, false);
})();
