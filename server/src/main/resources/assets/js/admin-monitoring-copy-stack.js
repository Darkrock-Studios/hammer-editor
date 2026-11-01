// Delegated so it also covers rows swapped in by HTMX (ignore/unignore refresh).
document.addEventListener("click", function (event) {
	var btn = event.target.closest("[data-copy-stack]");
	if (!btn) return;

	var wrap = btn.closest(".mon-error__stack-wrap");
	var pre = wrap && wrap.querySelector(".mon-error__stack");
	if (!pre) return;

	var text = pre.textContent;
	var original = btn.innerHTML;
	var showCopied = function () {
		btn.classList.add("copied");
		btn.innerHTML = '<i class="fa-solid fa-check"></i> ' + btn.dataset.copiedLabel;
		setTimeout(function () {
			btn.classList.remove("copied");
			btn.innerHTML = original;
		}, 2000);
	};

	// navigator.clipboard is undefined on insecure (plain HTTP) self-hosted origins.
	var fallback = function () {
		var range = document.createRange();
		range.selectNodeContents(pre);
		var sel = window.getSelection();
		sel.removeAllRanges();
		sel.addRange(range);
		try { document.execCommand("copy"); showCopied(); } catch (err) { /* nothing to do */ }
		sel.removeAllRanges();
	};

	if (navigator.clipboard && navigator.clipboard.writeText) {
		navigator.clipboard.writeText(text).then(showCopied, fallback);
	} else {
		fallback();
	}
});
