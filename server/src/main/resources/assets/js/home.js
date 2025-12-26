// Linux download format selector
(function () {
	const select = document.getElementById('linux-format-select');
	const btn = document.getElementById('linux-download-btn');
	const icon = document.getElementById('linux-download-icon');

	if (select && btn && icon) {
		select.addEventListener('change', function () {
			const selected = this.options[this.selectedIndex];
			btn.href = selected.dataset.url;
			icon.className = selected.dataset.icon;
		});
	}
})();
