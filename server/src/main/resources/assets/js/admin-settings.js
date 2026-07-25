// Admin settings page: keeps the monitoring subfields in step with their enabling toggles.
(function () {
	const monToggle = document.getElementById('monEnabled');
	const subfields = document.getElementById('monSubfields');
	const alertToggle = document.getElementById('monAlertEmailEnabled');
	const alertEmail = document.getElementById('monAlertEmail');

	if (!monToggle || !subfields || !alertToggle || !alertEmail) return;

	function syncMon() {
		const off = !monToggle.checked;
		subfields.classList.toggle('mon-subfields--disabled', off);
		subfields.querySelectorAll('input').forEach(function (i) {
			i.disabled = off;
		});
	}

	function syncAlert() {
		const active = alertToggle.checked && monToggle.checked;
		alertEmail.disabled = !active;
		alertEmail.required = active;
	}

	monToggle.addEventListener('change', syncMon);
	monToggle.addEventListener('change', syncAlert);
	alertToggle.addEventListener('change', syncAlert);
	syncMon();
	syncAlert();

	const form = document.querySelector('form[action="/admin/settings"]');
	if (form) {
		form.addEventListener('htmx:before-request', function (e) {
			if (!e.target.reportValidity()) e.preventDefault();
		});
	}
})();
