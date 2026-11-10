// Copy buttons: a button with data-copy="<selector>" copies the text of the matching element
// inside its parent, then shows data-copied-label or data-copy-failed-label for two seconds.
// Delegated so it also covers buttons swapped in by HTMX. Needs copy-button-logic.js.
document.addEventListener('click', function (event) {
	var btn = event.target.closest('[data-copy]');
	if (!btn) return;

	var source = btn.parentElement && btn.parentElement.querySelector(btn.dataset.copy);
	if (!source) return;

	// Captured once, so a click during the feedback window can't save the feedback as the label.
	if (btn.dataset.originalHtml === undefined) btn.dataset.originalHtml = btn.innerHTML;

	var execCopy = function () {
		var range = document.createRange();
		range.selectNodeContents(source);
		var sel = window.getSelection();
		sel.removeAllRanges();
		sel.addRange(range);
		try {
			return document.execCommand('copy');
		} finally {
			sel.removeAllRanges();
		}
	};

	copyText(source.textContent, navigator.clipboard, execCopy).then(function (ok) {
		clearTimeout(btn._copyResetTimer);
		btn.classList.toggle('copied', ok);
		btn.classList.toggle('copy-failed', !ok);
		var label = ok ? btn.dataset.copiedLabel : btn.dataset.copyFailedLabel;
		btn.innerHTML = '<i class="fa-solid ' + (ok ? 'fa-check' : 'fa-xmark') + '"></i> ' + label;
		btn._copyResetTimer = setTimeout(function () {
			btn.classList.remove('copied', 'copy-failed');
			btn.innerHTML = btn.dataset.originalHtml;
		}, 2000);
	});
});
