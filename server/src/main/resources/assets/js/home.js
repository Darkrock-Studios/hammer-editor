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
			});
		}
	}

	setupDownloadSelector('windows-format-select', 'windows-download-btn', 'windows-download-icon');
	setupDownloadSelector('macos-format-select', 'macos-download-btn', 'macos-download-icon');
	setupDownloadSelector('linux-format-select', 'linux-download-btn', 'linux-download-icon');
	setupDownloadSelector('android-format-select', 'android-download-btn', 'android-download-icon');
})();
