document.getElementById('email-test-form').addEventListener('htmx:beforeRequest', function () {
	document.getElementById('email-result').replaceChildren();
});
