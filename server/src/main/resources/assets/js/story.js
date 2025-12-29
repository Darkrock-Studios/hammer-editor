/**
 * Story Page JavaScript
 * Handles settings panel interactions, copy URL functionality, and share dialog
 */

document.addEventListener('DOMContentLoaded', function () {
	initSettingsPanel();
	initCopyUrl();
	initShareDialog();
	initPublishWarning();
	initSceneSelector();
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

/**
 * Initialize publish warning dialog functionality
 */
function initPublishWarning() {
	// Close dialog on Escape key
	document.addEventListener('keydown', function (e) {
		if (e.key === 'Escape') {
			closePublishWarning();
		}
	});
}

/**
 * Handle publish toggle change - show warning when enabling
 * @param {HTMLInputElement} checkbox - The publish toggle checkbox
 */
function handlePublishToggle(checkbox) {
	if (checkbox.checked) {
		// User is trying to enable publish - show warning dialog
		checkbox.checked = false; // Reset the toggle until confirmed
		showPublishWarning();
	}
}

/**
 * Show the publish warning dialog
 */
function showPublishWarning() {
	const container = document.getElementById('publish-warning-container');
	const publishSection = document.getElementById('publish-section');

	if (!container || !publishSection) return;

	const projectUuid = publishSection.dataset.projectUuid;

	// Fetch the dialog template via HTMX
	htmx.ajax('GET', '/story/' + projectUuid + '/publish-warning', {
		target: '#publish-warning-container',
		swap: 'innerHTML'
	});
}

/**
 * Close the publish warning dialog with animation
 * @param {Event} event - Optional click event (for overlay clicks)
 */
function closePublishWarning(event) {
	// If event is provided and it's not on the overlay itself, ignore
	if (event && event.target !== event.currentTarget) {
		return;
	}

	const overlay = document.getElementById('publish-warning-overlay');
	if (overlay) {
		overlay.classList.add('closing');
		setTimeout(function () {
			const container = document.getElementById('publish-warning-container');
			if (container) {
				container.innerHTML = '';
			}
		}, 200);
	}
}

/**
 * Confirm publish action - proceed with the HTMX request
 */
function confirmPublish() {
	const publishSection = document.getElementById('publish-section');
	if (!publishSection) return;

	const projectUuid = publishSection.dataset.projectUuid;

	// Close the warning dialog
	closePublishWarning();

	// Wait for dialog animation to complete, then trigger the publish
	setTimeout(function () {
		htmx.ajax('POST', '/story/' + projectUuid + '/publish', {
			target: '#publish-section',
			swap: 'outerHTML'
		});
	}, 220);
}

/**
 * Initialize scene selector functionality
 * Scrolls content to top when a new scene is loaded
 */
function initSceneSelector() {
	const contentArea = document.getElementById('story-content-area');
	if (!contentArea) return;

	// Listen for HTMX swap events on the content area
	contentArea.addEventListener('htmx:afterSwap', function () {
		// Smooth scroll to the top of the content area
		contentArea.scrollIntoView({behavior: 'smooth', block: 'start'});
	});
}
