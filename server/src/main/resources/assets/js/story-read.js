/**
 * Browser wiring for the author's chapter reader: the contents dialog, arrow-key chapter turns, and
 * remembering the last chapter for the story page's "Continue reading" link. Reading prefs are
 * reader-prefs.js, loaded before this with story-read-logic.js.
 */
/* global readerStorage, lastChapterKey, isChapterKey */
(function () {
	var reader = document.getElementById('reader');
	if (!reader) return;

	var projectKey = reader.dataset.projectKey;
	var chapter = reader.dataset.chapter;
	if (projectKey && chapter) readerStorage.set(lastChapterKey(projectKey), chapter);

	var toc = document.getElementById('reader-toc');

	function openContents() {
		if (!toc || typeof toc.showModal !== 'function') return;
		toc.showModal();
		var current = toc.querySelector('[aria-current="page"]');
		if (current) current.focus();
	}

	function closeContents() {
		if (toc && toc.open) toc.close();
	}

	// A Map, so a data-reader-action value can only name one of these, never an inherited property.
	var actions = new Map([
		['contents', openContents],
		['close-contents', closeContents],
	]);

	document.addEventListener('click', function (event) {
		var button = event.target.closest('[data-reader-action]');
		var action = button && actions.get(button.dataset.readerAction);
		if (action) {
			action();
			return;
		}
		// A scene link jumps within this page, so the dialog gets out of the way.
		if (event.target.closest('[data-reader-scene-link]')) closeContents();
	});

	if (toc) {
		// A click on the backdrop lands on the dialog element itself, outside its content box.
		toc.addEventListener('click', function (event) {
			if (event.target !== toc) return;
			var box = toc.getBoundingClientRect();
			var inside = event.clientX >= box.left && event.clientX <= box.right &&
				event.clientY >= box.top && event.clientY <= box.bottom;
			if (!inside) closeContents();
		});
	}

	document.addEventListener('keydown', function (event) {
		if (toc && toc.open) return;
		var target = event.target;
		var interactive = !!(target && target.closest && (
			target.isContentEditable ||
			target.closest('a, button, input, select, textarea, summary, [role="toolbar"], [tabindex]')
		));
		if (!isChapterKey(event, interactive)) return;

		var link = reader.querySelector(event.key === 'ArrowLeft' ? '[data-reader-prev]' : '[data-reader-next]');
		if (link) window.location.href = link.href;
	});
})();
