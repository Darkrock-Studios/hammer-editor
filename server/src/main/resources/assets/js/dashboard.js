// Top-level functions in this file are wired to HTML elements from mustache
// templates (onclick=..., hx-* attributes) — ESLint can't see those references.
/* eslint-disable no-unused-vars */
/* global htmx */

// Pen Name validation state
const PenNameState = {
	MIN_LENGTH: 4,
	MAX_LENGTH: 32,
	// Unicode letters, numbers, spaces, hyphens, underscores - must start with letter
	PATTERN: /^[\p{L}][\p{L}\p{N} _-]*$/u,
	checkTimeout: null,
	isValid: false,
	isAvailable: false,
	isChecking: false
};

function enterPenNameEditMode() {
	document.getElementById('pen-name-display-state').classList.add('hidden');
	document.getElementById('pen-name-edit-state').classList.remove('hidden');
	const input = document.getElementById('pen-name-input');
	input.focus();
	updateCharCount(input.value);
}

function exitPenNameEditMode() {
	document.getElementById('pen-name-edit-state').classList.add('hidden');
	document.getElementById('pen-name-display-state').classList.remove('hidden');
	resetValidationState();
}

function resetValidationState() {
	PenNameState.isValid = false;
	PenNameState.isAvailable = false;
	PenNameState.isChecking = false;
	updateSubmitButton();
	hideValidationFeedback();
}

function updateCharCount(value) {
	const count = value.length;
	const countEl = document.getElementById('char-count');
	countEl.textContent = `${count}/${PenNameState.MAX_LENGTH}`;
	countEl.classList.toggle('at-limit', count >= PenNameState.MAX_LENGTH);
	countEl.classList.toggle('near-limit', count >= PenNameState.MAX_LENGTH - 5 && count < PenNameState.MAX_LENGTH);
}

function validateClientSide(penName) {
	const trimmed = penName.trim();

	if (trimmed.length === 0) {
		return {valid: false, message: ''};
	}
	if (trimmed.length < PenNameState.MIN_LENGTH) {
		return {valid: false, message: `At least ${PenNameState.MIN_LENGTH} characters needed`};
	}
	if (trimmed.length > PenNameState.MAX_LENGTH) {
		return {valid: false, message: `Maximum ${PenNameState.MAX_LENGTH} characters allowed`};
	}
	if (!PenNameState.PATTERN.test(trimmed)) {
		return {
			valid: false,
			message: 'Must start with a letter. Only letters, numbers, spaces, hyphens, and underscores allowed.'
		};
	}
	return {valid: true, message: ''};
}

function showValidationFeedback(type, message) {
	const feedback = document.getElementById('validation-feedback');
	const icon = feedback.querySelector('.feedback-icon');
	const msg = feedback.querySelector('.feedback-message');

	feedback.className = 'validation-feedback';
	feedback.classList.add('visible', type);

	if (type === 'success') {
		icon.innerHTML = '&#10003;'; // Checkmark
	} else if (type === 'error') {
		icon.innerHTML = '&#10007;'; // X mark
	} else if (type === 'checking') {
		icon.innerHTML = '<span class="spinner"></span>';
	}

	msg.textContent = message;
}

function hideValidationFeedback() {
	const feedback = document.getElementById('validation-feedback');
	feedback.className = 'validation-feedback';
}

function updateSubmitButton() {
	const btn = document.getElementById('set-pen-name-btn');
	const canSubmit = PenNameState.isValid && PenNameState.isAvailable && !PenNameState.isChecking;
	btn.disabled = !canSubmit;
}

function checkAvailability(penName) {
	PenNameState.isChecking = true;
	showValidationFeedback('checking', window.Messages.penname.checkingAvailability);
	updateSubmitButton();

	htmx.ajax('GET', `/dashboard/penname/check?penName=${encodeURIComponent(penName)}`, {
		swap: 'none',
		handler: function (elt, info) {
			try {
				const data = JSON.parse(info.xhr.responseText);

				PenNameState.isChecking = false;
				PenNameState.isValid = data.valid;
				PenNameState.isAvailable = data.available;

				if (data.valid && data.available) {
					showValidationFeedback('success', window.Messages.penname.available);
				} else {
					showValidationFeedback('error', data.message);
				}
			} catch (error) {
				PenNameState.isChecking = false;
				PenNameState.isValid = false;
				PenNameState.isAvailable = false;
				showValidationFeedback('error', window.Messages.penname.checkFailed);
			}

			updateSubmitButton();
		}
	});
}

function onPenNameInput(value) {
	updateCharCount(value);

	// Clear any pending server check
	if (PenNameState.checkTimeout) {
		clearTimeout(PenNameState.checkTimeout);
	}

	const trimmed = value.trim();

	// Reset state
	PenNameState.isValid = false;
	PenNameState.isAvailable = false;

	if (trimmed.length === 0) {
		hideValidationFeedback();
		updateSubmitButton();
		return;
	}

	// Client-side validation first
	const clientResult = validateClientSide(value);
	if (!clientResult.valid) {
		if (clientResult.message) {
			showValidationFeedback('error', clientResult.message);
		}
		updateSubmitButton();
		return;
	}

	// Debounce server check
	showValidationFeedback('checking', window.Messages.penname.checkingAvailability);
	PenNameState.checkTimeout = setTimeout(() => {
		checkAvailability(trimmed);
	}, 300);
}

function validateBeforeSubmit() {
	return PenNameState.isValid && PenNameState.isAvailable && !PenNameState.isChecking;
}

// ========================================
// Release Pen Name Functions
// ========================================

function enterReleaseMode() {
	document.getElementById('pen-name-display-state').classList.add('hidden');
	document.getElementById('pen-name-release-state').classList.remove('hidden');
	document.getElementById('release-confirm-field').value = '';
	document.getElementById('release-btn').disabled = true;
	document.getElementById('release-confirm-field').focus();
}

function exitReleaseMode() {
	document.getElementById('pen-name-release-state').classList.add('hidden');
	document.getElementById('pen-name-display-state').classList.remove('hidden');
	document.getElementById('release-confirm-field').value = '';
	document.getElementById('release-btn').disabled = true;
}

function onReleaseConfirmInput(value) {
	const currentPenName = document.getElementById('pen-name-value').textContent.trim();
	const matches = value.trim() === currentPenName;
	document.getElementById('release-btn').disabled = !matches;
}

function confirmRelease() {
	const currentPenName = document.getElementById('pen-name-value').textContent.trim();
	return confirm(window.Messages.penname.releaseConfirm.replace('{0}', currentPenName));
}

// Handle successful pen name update
document.body.addEventListener('penNameUpdated', function (evt) {
	const section = document.getElementById('pen-name-section');
	const input = document.getElementById('pen-name-input');
	const newPenName = input.value.trim();
	const penNameValue = document.getElementById('pen-name-value');
	const penNameCurrent = document.querySelector('.pen-name-current');
	const claimBtn = document.getElementById('claim-pen-name-btn');

	// Update the display state to show the new pen name
	section.dataset.haspenname = 'true';

	// Hide claim button
	if (claimBtn) {
		claimBtn.classList.add('hidden');
	}

	// Show and update the pen name value
	penNameValue.textContent = newPenName;
	if (penNameCurrent) {
		penNameCurrent.classList.remove('hidden');
	}

	// Update release confirmation display
	document.getElementById('release-current-penname').textContent = newPenName;

	// Show the bio section (it's hidden by default when no pen name is set)
	const bioSection = document.getElementById('bio-section');
	if (bioSection) {
		bioSection.style.display = '';
	}

	// Show the community section (it's hidden by default when no pen name is set)
	const communitySection = document.getElementById('community-section');
	if (communitySection) {
		communitySection.style.display = '';
	}

	exitPenNameEditMode();
});

// Initialize char count on page load
document.addEventListener('DOMContentLoaded', function () {
	const input = document.getElementById('pen-name-input');
	if (input && input.value) {
		updateCharCount(input.value);
	}

	// Handle successful pen name release
	const releaseForm = document.getElementById('release-form');
	if (releaseForm) {
		releaseForm.addEventListener('htmx:afterRequest', function (evt) {
			if (evt.detail.successful) {
				const section = document.getElementById('pen-name-section');
				const penNameValue = document.getElementById('pen-name-value');
				const penNameCurrent = document.querySelector('.pen-name-current');
				const claimBtn = document.getElementById('claim-pen-name-btn');

				// Update state to "no pen name"
				section.dataset.haspenname = 'false';

				// Hide pen name display elements
				if (penNameValue) {
					penNameValue.textContent = '';
				}
				if (penNameCurrent) {
					penNameCurrent.classList.add('hidden');
				}

				// Show claim button
				if (claimBtn) {
					claimBtn.classList.remove('hidden');
				}

				// Clear the edit input
				const penNameInput = document.getElementById('pen-name-input');
				if (penNameInput) {
					penNameInput.value = '';
				}

				// Hide the bio section when pen name is released
				const bioSection = document.getElementById('bio-section');
				if (bioSection) {
					bioSection.style.display = 'none';
				}

				// Hide the community section when pen name is released
				const communitySection = document.getElementById('community-section');
				if (communitySection) {
					communitySection.style.display = 'none';
				}

				// Exit release mode and go back to display state
				exitReleaseMode();
			}
		});
	}
});

// ========================================
// Bio Management Functions
// ========================================

function enterBioEditMode() {
	document.getElementById('bio-display-state').classList.add('hidden');
	document.getElementById('bio-edit-state').classList.remove('hidden');
	const textarea = document.getElementById('bio-input');
	textarea.focus();
	updateBioCharCount(textarea.value);
}

function exitBioEditMode() {
	document.getElementById('bio-edit-state').classList.add('hidden');
	document.getElementById('bio-display-state').classList.remove('hidden');
}

function updateBioCharCount(value) {
	const count = value.length;
	const countEl = document.getElementById('bio-char-count');
	countEl.textContent = `${count}/1024`;

	// Apply warning styles
	countEl.classList.toggle('at-limit', count >= 1024);
	countEl.classList.toggle('near-limit', count >= 924 && count < 1024);
}

function onBioInput(value) {
	updateBioCharCount(value);
}

function confirmClearBio() {
	if (confirm(window.Messages.bio.clearConfirm)) {
		htmx.ajax('DELETE', '/dashboard/bio', {
			target: '#bio-section',
			swap: 'innerHTML'
		});
	}
}

// Initialize bio char count on page load
document.addEventListener('DOMContentLoaded', function () {
	const bioInput = document.getElementById('bio-input');
	if (bioInput && bioInput.value) {
		updateBioCharCount(bioInput.value);
	}
});

// ========================================
// Community Membership Functions
// ========================================

function toggleCommunityMembership(isJoining) {
	const endpoint = isJoining ? '/dashboard/community/join' : '/dashboard/community/leave';

	htmx.ajax('POST', endpoint, {
		target: '#community-section',
		swap: 'innerHTML'
	});
}