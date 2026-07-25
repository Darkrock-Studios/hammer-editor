// Admin whitelist page: the add form's expiry preset, and the edit-reason / edit-expiry dialogs.
/* global hammerActions */
(function () {
	function toggleExpiryDate(select, fieldId) {
		document.getElementById(fieldId).classList.toggle('hidden', select.value !== 'custom');
	}

	// form.reset() restores the select without firing change, so visibility is re-synced by hand.
	function syncAddExpiryDateVisibility() {
		toggleExpiryDate(document.getElementById('add-expiry-preset'), 'add-expiry-date-field');
	}

	function openDialog(id) {
		const dialog = document.getElementById(id);
		dialog.classList.remove('hidden', 'closing');
		return dialog;
	}

	function closeDialog(id) {
		const dialog = document.getElementById(id);
		dialog.classList.add('closing');
		setTimeout(function () {
			dialog.classList.add('hidden');
			dialog.classList.remove('closing');
		}, 200);
	}

	function openEditExpiryDialog(btn) {
		const email = btn.dataset.email;
		const expiresRaw = btn.dataset.expiresRaw;

		document.getElementById('edit-expiry-email-display').textContent = email;
		document.getElementById('edit-expiry-current').textContent = btn.dataset.expires;
		document.getElementById('edit-expiry-email').value = email;
		document.getElementById('edit-expiry-page').value = btn.dataset.page;
		document.getElementById('edit-expiry-sort').value = btn.dataset.sortOldestFirst;

		// Default to a no-op: an entry with an expiry opens on "specific date" showing
		// its current value; an entry with no expiry opens on "Never".
		const preset = document.getElementById('edit-expiry-preset');
		preset.value = expiresRaw ? 'custom' : 'never';
		document.getElementById('edit-expiry-date').value = expiresRaw || '';
		toggleExpiryDate(preset, 'edit-expiry-date-field');

		openDialog('edit-expiry-dialog');
		setTimeout(function () { preset.focus(); }, 100);
	}

	function openEditReasonDialog(btn) {
		document.getElementById('edit-dialog-email-display').textContent = btn.dataset.email;
		document.getElementById('edit-reason-email').value = btn.dataset.email;
		document.getElementById('edit-reason-input').value = btn.dataset.reason;
		document.getElementById('edit-reason-page').value = btn.dataset.page;
		document.getElementById('edit-reason-sort').value = btn.dataset.sortOldestFirst;

		openDialog('edit-reason-dialog');

		// Focus after the open animation.
		setTimeout(function () {
			const input = document.getElementById('edit-reason-input');
			input.focus();
			input.select();
		}, 100);
	}

	hammerActions({
		'expiry-preset': (el) => toggleExpiryDate(el, el.dataset.expiryField),
		'edit-reason-open': openEditReasonDialog,
		'edit-reason-close': () => closeDialog('edit-reason-dialog'),
		'edit-expiry-open': openEditExpiryDialog,
		'edit-expiry-close': () => closeDialog('edit-expiry-dialog'),
		'dialog-dismiss': (el, event) => {
			if (event.target === el) closeDialog(el.id);
		}
	});

	// Replaces hx-on::after-request on the three forms, which htmx compiles with new Function().
	document.body.addEventListener('htmx:afterRequest', function (event) {
		const form = event.target.closest('form');
		if (!form) return;

		if (form.id === 'add-whitelist-form') {
			form.reset();
			syncAddExpiryDateVisibility();
		} else if (form.closest('#edit-reason-dialog')) {
			closeDialog('edit-reason-dialog');
		} else if (form.closest('#edit-expiry-dialog')) {
			closeDialog('edit-expiry-dialog');
		}
	});

	// Keep the add form's hidden paging inputs in step with the loaded list.
	document.body.addEventListener('htmx:afterSwap', function (event) {
		if (event.detail.target.id !== 'whitelist') return;

		const urlParams = new URLSearchParams(window.location.search);
		document.getElementById('add-form-page').value = urlParams.get('page') || '0';
		document.getElementById('add-form-sort').value = urlParams.get('sortOldestFirst') || 'false';
	});

	document.addEventListener('keydown', function (e) {
		if (e.key !== 'Escape') return;

		['edit-reason-dialog', 'edit-expiry-dialog'].forEach(function (id) {
			if (!document.getElementById(id).classList.contains('hidden')) closeDialog(id);
		});
	});
})();
