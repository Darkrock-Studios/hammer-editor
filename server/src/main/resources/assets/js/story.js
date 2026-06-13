/**
 * Story Page JavaScript
 * Handles settings panel interactions, copy URL functionality, and share dialog
 */

// Top-level functions in this file are wired to HTML elements from mustache
// templates (onclick=..., hx-* attributes) — ESLint can't see those references.
/* eslint-disable no-unused-vars */
/* global htmx */

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

			const iconEl = copyBtn.querySelector('i');
			const originalIconClass = iconEl ? iconEl.className : 'fas fa-copy';
			copyBtn.classList.add('copied');
			if (iconEl) iconEl.className = 'fas fa-check';

			setTimeout(function () {
				copyBtn.classList.remove('copied');
				if (iconEl) iconEl.className = originalIconClass;
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

	const projectNameForUrl = publishSection.dataset.projectNameForUrl;

	// Fetch the dialog template via HTMX
	htmx.ajax('GET', '/story/' + projectNameForUrl + '/publish-warning', {
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

	const projectNameForUrl = publishSection.dataset.projectNameForUrl;

	// Close the warning dialog
	closePublishWarning();

	// Wait for dialog animation to complete, then trigger the publish
	setTimeout(function () {
		htmx.ajax('POST', '/story/' + projectNameForUrl + '/publish', {
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

/* ===== Editorial Review dialog ===== */

// The dialog arrives via HTMX swap, so all listeners are delegated to document.

/**
 * Close the review dialog with animation
 * @param {Event} event - Optional click event (for overlay clicks)
 */
function closeReviewDialog(event) {
	if (event && event.target !== event.currentTarget) {
		return;
	}

	const overlay = document.getElementById('review-dialog-overlay');
	if (overlay) {
		overlay.classList.add('closing');
		setTimeout(function () {
			const container = document.getElementById('review-dialog-container');
			if (container) {
				container.innerHTML = '';
			}
		}, 200);
	}
}

/**
 * Copy the review link (no-email fallback dialog)
 */
function copyReviewLink() {
	const input = document.getElementById('review-link-url');
	const button = document.getElementById('review-link-copy');
	if (!input || !button) return;

	const showCopied = function () {
		const original = button.innerHTML;
		button.innerHTML = '<i class="fa-solid fa-check"></i> ' + button.dataset.copiedLabel;
		setTimeout(function () { button.innerHTML = original; }, 2000);
	};

	// navigator.clipboard is undefined on insecure (plain HTTP) self-hosted
	// origins; fall back to execCommand like the publish copy button does.
	const fallback = function () {
		input.select();
		try { document.execCommand('copy'); showCopied(); } catch (err) { /* nothing to do */ }
	};

	if (navigator.clipboard && navigator.clipboard.writeText) {
		navigator.clipboard.writeText(input.value).then(showCopied, fallback);
	} else {
		fallback();
	}
}

document.addEventListener('keydown', function (e) {
	if (e.key === 'Escape') closeReviewDialog();
});

// Scene checkbox changes: update the count and gate the send button
document.addEventListener('change', function (e) {
	const form = document.getElementById('review-request-form');
	if (!form || !form.contains(e.target)) return;
	updateReviewFormState(form);
});

document.addEventListener('input', function (e) {
	const form = document.getElementById('review-request-form');
	if (!form || !form.contains(e.target)) return;
	updateReviewFormState(form);
});

/** Every scene checkbox nested under a group toggle, to the next sibling at or above its depth. */
function groupToggleBoxes(toggle) {
	const groupDepth = parseInt(toggle.dataset.groupDepth, 10);
	const groupRow = toggle.closest('.review-scene-group');
	const boxes = [];
	let node = groupRow ? groupRow.nextElementSibling : null;
	while (node) {
		if (node.classList.contains('review-scene-group')) {
			const depth = parseInt(node.querySelector('.review-scene-group__toggle')?.dataset.groupDepth ?? '0', 10);
			if (depth <= groupDepth) break;
		} else if (node.classList.contains('review-scene-row')) {
			const depth = parseInt(node.dataset.depth ?? '0', 10);
			if (depth <= groupDepth) break;
			boxes.push(node.querySelector('.review-scene-row__check'));
		}
		node = node.nextElementSibling;
	}
	return boxes;
}

// Group "Select all"/"Clear" buttons toggle every scene nested under the group
document.addEventListener('click', function (e) {
	const toggle = e.target.closest('.review-scene-group__toggle');
	if (!toggle) return;
	e.preventDefault();

	toggleBoxes(groupToggleBoxes(toggle));

	const form = document.getElementById('review-request-form');
	if (form) updateReviewFormState(form);
});

// Master "Select all"/"Clear all" toggles every scene in the project
document.addEventListener('click', function (e) {
	const master = e.target.closest('.review-scene-master__toggle');
	if (!master) return;
	e.preventDefault();

	const tree = document.getElementById('review-scene-tree');
	if (!tree) return;
	toggleBoxes(Array.from(tree.querySelectorAll('.review-scene-row__check')));

	const form = document.getElementById('review-request-form');
	if (form) updateReviewFormState(form);
});

/**
 * Check all boxes if any are unchecked; otherwise clear them all.
 * @param {HTMLInputElement[]} boxes - Checkbox inputs to toggle together
 */
function toggleBoxes(boxes) {
	const anyUnchecked = boxes.some(function (b) { return b && !b.checked; });
	boxes.forEach(function (b) { if (b) b.checked = anyUnchecked; });
}

/**
 * Update the selected-scene count, the toggle button labels, and the send button
 * @param {HTMLFormElement} form - The review request form
 */
function updateReviewFormState(form) {
	const allBoxes = Array.from(form.querySelectorAll('.review-scene-row__check'));
	const checked = allBoxes.filter(function (b) { return b.checked; }).length;
	const email = form.querySelector('#review-email');
	const sendBtn = form.querySelector('#review-send-btn');
	const counter = document.getElementById('review-scene-count');

	if (counter) {
		const total = counter.dataset.total;
		counter.textContent = checked === 0
			? counter.dataset.noneLabel || 'none selected'
			: checked + ' of ' + total + ' selected';
	}

	// Master toggle reads "Clear all" once everything is selected
	const master = document.getElementById('review-select-all');
	if (master) {
		const allChecked = allBoxes.length > 0 && checked === allBoxes.length;
		master.textContent = allChecked ? master.dataset.clearLabel : master.dataset.selectLabel;
	}

	// Each group toggle likewise flips to "Clear" once its whole subtree is checked,
	// so it never silently clears scenes while still labelled "Select all".
	form.querySelectorAll('.review-scene-group__toggle').forEach(function (toggle) {
		const boxes = groupToggleBoxes(toggle);
		const allChecked = boxes.length > 0 && boxes.every(function (b) { return b && b.checked; });
		toggle.textContent = allChecked ? toggle.dataset.clearLabel : toggle.dataset.selectLabel;
	});

	if (sendBtn) {
		sendBtn.disabled = checked === 0 || !email || !email.value.includes('@');
	}
}
