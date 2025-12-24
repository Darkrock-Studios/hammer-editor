/**
 * Story Page JavaScript
 * Handles settings panel interactions, copy URL functionality, and share dialog
 */

document.addEventListener('DOMContentLoaded', function () {
	initSettingsPanel();
	initCopyUrl();
	initShareDialog();
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

/**
 * Initialize share dialog functionality
 */
function initShareDialog() {
	// Close dialog on Escape key
	document.addEventListener('keydown', function (e) {
		if (e.key === 'Escape') {
			closeShareDialog();
		}
	});
}

/**
 * Close the share dialog with animation
 * @param {Event} event - Optional click event (for overlay clicks)
 */
function closeShareDialog(event) {
	// If event is provided and it's not on the overlay itself, ignore
	if (event && event.target !== event.currentTarget) {
		return;
	}

	const overlay = document.getElementById('share-dialog-overlay');
	if (overlay) {
		overlay.classList.add('closing');
		setTimeout(function () {
			const container = document.getElementById('share-dialog-container');
			if (container) {
				container.innerHTML = '';
			}
		}, 200);
	}
}

/**
 * Toggle password visibility in access list
 * @param {HTMLElement} button - The toggle button that was clicked
 */
function togglePasswordVisibility(button) {
	const container = button.closest('.access-password-display');
	if (!container) return;

	const masked = container.querySelector('.password-masked');
	const revealed = container.querySelector('.password-revealed');
	const icon = button.querySelector('i');

	if (!masked || !revealed || !icon) return;

	const isRevealed = revealed.style.display !== 'none';

	if (isRevealed) {
		// Hide password
		masked.style.display = '';
		revealed.style.display = 'none';
		icon.classList.remove('fa-eye-slash');
		icon.classList.add('fa-eye');
		button.classList.remove('revealed');
	} else {
		// Show password
		masked.style.display = 'none';
		revealed.style.display = '';
		icon.classList.remove('fa-eye');
		icon.classList.add('fa-eye-slash');
		button.classList.add('revealed');
	}
}
