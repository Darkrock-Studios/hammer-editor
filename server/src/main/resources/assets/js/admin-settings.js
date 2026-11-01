(function () {
	var monToggle = document.getElementById('monEnabled');
	var subfields = document.getElementById('monSubfields');
	function syncMon() {
		var off = !monToggle.checked;
		subfields.classList.toggle('mon-subfields--disabled', off);
		subfields.querySelectorAll('input').forEach(function(i) { i.disabled = off; });
	}
	monToggle.addEventListener('change', syncMon);
	syncMon();

	var alertToggle = document.getElementById('monAlertEmailEnabled');
	var alertEmail = document.getElementById('monAlertEmail');
	function syncAlert() {
		var active = alertToggle.checked && monToggle.checked;
		alertEmail.disabled = !active;
		alertEmail.required = active;
	}
	alertToggle.addEventListener('change', syncAlert);
	monToggle.addEventListener('change', syncAlert);
	syncAlert();

	document.querySelector('form[action="/admin/settings"]').addEventListener('htmx:before-request', function (e) {
		if (!e.target.reportValidity()) e.preventDefault();
	});
})();
