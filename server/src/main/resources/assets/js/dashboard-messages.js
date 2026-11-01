// Localized strings for dashboard.js, rendered into data attributes by the template.
window.Messages = (function () {
	var d = document.getElementById('dashboard-messages').dataset;
	return {
		penname: {
			checkingAvailability: d.pennameChecking,
			available: d.pennameAvailable,
			checkFailed: d.pennameCheckFailed,
			releaseConfirm: d.pennameReleaseConfirm
		},
		bio: {
			clearConfirm: d.bioClearConfirm
		}
	};
})();
