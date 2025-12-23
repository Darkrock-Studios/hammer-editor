/**
 * Story Page JavaScript
 * Handles settings panel interactions and copy URL functionality
 */

document.addEventListener('DOMContentLoaded', function () {
	initSettingsPanel();
	initCopyUrl();
});

/**
 * Initialize the collapsible settings panel
 */
function initSettingsPanel() {
	const toggleBtn = document.getElementById('settings-toggle-btn');
	const settingsContent = document.getElementById('settings-content');

	if (!toggleBtn || !settingsContent) return;

	toggleBtn.addEventListener('click', function () {
		const isExpanded = toggleBtn.getAttribute('aria-expanded') === 'true';

		toggleBtn.setAttribute('aria-expanded', !isExpanded);
		settingsContent.setAttribute('aria-hidden', isExpanded);
		settingsContent.classList.toggle('open', !isExpanded);
	});
}

/**
 * Initialize copy URL functionality.
 * Uses event delegation to handle dynamically swapped content from HTMX.
 */
function initCopyUrl() {
	// Use event delegation on the document to handle dynamically inserted buttons
	document.addEventListener('click', async function (event) {
		const copyBtn = event.target.closest('#copy-url-btn');
		if (!copyBtn) return;

		const urlInput = document.getElementById('publish-url-input');
		if (!urlInput) return;

		try {
			await navigator.clipboard.writeText(urlInput.value);

			// Visual feedback
			copyBtn.classList.add('copied');
			const originalIcon = copyBtn.innerHTML;
			copyBtn.innerHTML = '<i class="fas fa-check"></i>';

			setTimeout(function () {
				copyBtn.classList.remove('copied');
				copyBtn.innerHTML = originalIcon;
			}, 2000);
		} catch (err) {
			// Fallback for older browsers
			urlInput.select();
			document.execCommand('copy');

			copyBtn.classList.add('copied');
			setTimeout(function () {
				copyBtn.classList.remove('copied');
			}, 2000);
		}
	});
}
