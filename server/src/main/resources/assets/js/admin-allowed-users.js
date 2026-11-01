function toggleExpiryDate(select, fieldId) {
	const field = document.getElementById(fieldId);
	field.classList.toggle('hidden', select.value !== 'custom');
}

// form.reset() restores the select without firing onchange, so visibility is re-synced by hand.
function syncAddExpiryDateVisibility() {
	toggleExpiryDate(document.getElementById('add-expiry-preset'), 'add-expiry-date-field');
}

function openEditExpiryDialog(btn) {
	const email = btn.dataset.email;
	const expiresRaw = btn.dataset.expiresRaw;

	document.getElementById('edit-expiry-email-display').textContent = email;
	document.getElementById('edit-expiry-current').textContent = btn.dataset.expires;
	document.getElementById('edit-expiry-email').value = email;
	document.getElementById('edit-expiry-page').value = btn.dataset.page;
	document.getElementById('edit-expiry-sort').value = btn.dataset.sortOldestFirst;
	document.getElementById('edit-expiry-q').value = btn.dataset.q || '';

	// Default to a no-op: an entry with an expiry opens on "specific date" showing
	// its current value; an entry with no expiry opens on "Never".
	const preset = document.getElementById('edit-expiry-preset');
	preset.value = expiresRaw ? 'custom' : 'never';
	document.getElementById('edit-expiry-date').value = expiresRaw || '';
	toggleExpiryDate(preset, 'edit-expiry-date-field');

	const dialog = document.getElementById('edit-expiry-dialog');
	dialog.classList.remove('hidden', 'closing');

	setTimeout(() => preset.focus(), 100);
}

function closeEditExpiryDialog() {
	const dialog = document.getElementById('edit-expiry-dialog');
	dialog.classList.add('closing');
	setTimeout(() => {
		dialog.classList.add('hidden');
		dialog.classList.remove('closing');
	}, 200);
}

function openEditReasonDialog(btn) {
	const email = btn.dataset.email;
	const reason = btn.dataset.reason;
	const page = btn.dataset.page;
	const sortOldestFirst = btn.dataset.sortOldestFirst;

	document.getElementById('edit-dialog-email-display').textContent = email;
	document.getElementById('edit-reason-email').value = email;
	document.getElementById('edit-reason-input').value = reason;
	document.getElementById('edit-reason-page').value = page;
	document.getElementById('edit-reason-sort').value = sortOldestFirst;
	document.getElementById('edit-reason-q').value = btn.dataset.q || '';

	const dialog = document.getElementById('edit-reason-dialog');
	dialog.classList.remove('hidden', 'closing');

	// Focus the input after a brief delay to allow for animation
	setTimeout(() => {
		document.getElementById('edit-reason-input').focus();
		document.getElementById('edit-reason-input').select();
	}, 100);
}

function closeEditReasonDialog() {
	const dialog = document.getElementById('edit-reason-dialog');
	dialog.classList.add('closing');
	setTimeout(() => {
		dialog.classList.add('hidden');
		dialog.classList.remove('closing');
	}, 200);
}

function clearWhitelistSearch() {
	const input = document.getElementById('whitelist-search');
	input.value = '';
	htmx.trigger(input, 'search');
	input.focus();
}

// The add response is deliberately unfiltered, so the field is emptied to match.
function resetWhitelistSearchInput() {
	document.getElementById('whitelist-search').value = '';
}

// Only a successful add emits this, so a rejected one keeps the typed values and the filter.
document.body.addEventListener('whitelist-added', function () {
	document.getElementById('add-whitelist-form').reset();
	syncAddExpiryDateVisibility();
	resetWhitelistSearchInput();
});

// Close dialog on escape key
document.addEventListener('keydown', function (e) {
	if (e.key === 'Escape') {
		const dialog = document.getElementById('edit-reason-dialog');
		if (!dialog.classList.contains('hidden')) {
			closeEditReasonDialog();
		}
		const expiryDialog = document.getElementById('edit-expiry-dialog');
		if (!expiryDialog.classList.contains('hidden')) {
			closeEditExpiryDialog();
		}
	}
});

// Close dialog when clicking outside
document.getElementById('edit-reason-dialog').addEventListener('click', function (e) {
	if (e.target === this) {
		closeEditReasonDialog();
	}
});

document.getElementById('edit-expiry-dialog').addEventListener('click', function (e) {
	if (e.target === this) {
		closeEditExpiryDialog();
	}
});

document.getElementById('edit-reason-form').addEventListener('htmx:afterRequest', closeEditReasonDialog);
document.getElementById('edit-expiry-form').addEventListener('htmx:afterRequest', closeEditExpiryDialog);
