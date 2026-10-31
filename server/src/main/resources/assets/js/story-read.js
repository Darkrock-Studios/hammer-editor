/**
 * Browser wiring for the author's chapter reader: reading controls, the contents dialog, arrow-key
 * chapter turns, and remembering the last chapter for the story page's "Continue reading" link.
 * The preference rules live in story-read-logic.js (DOM-free, unit-tested), loaded before this.
 */
/* global READER_PREFS_KEY, READER_DEFAULT_FONT_SIZE, parseReaderPrefs, stepFontSize, nextWidth,
   lastChapterKey, isChapterKey */
(function () {
	var reader = document.getElementById('reader');
	if (!reader) return;

	// localStorage throws in some private modes and with storage disabled; the reader then works
	// with defaults and simply forgets.
	var storage = {
		get: function (key) {
			try {
				return localStorage.getItem(key);
			} catch (e) {
				return null;
			}
		},
		set: function (key, value) {
			try {
				localStorage.setItem(key, value);
			} catch (e) {
				// Best effort only.
			}
		},
	};

	var prefs = parseReaderPrefs(storage.get(READER_PREFS_KEY));
	var sizeLabel = reader.querySelector('[data-reader-size-label]');
	var headingsButton = reader.querySelector('[data-reader-action="headings"]');
	var widthButton = reader.querySelector('[data-reader-action="width"]');
	var widthLabel = reader.querySelector('[data-reader-width-label]');

	function apply() {
		reader.style.setProperty('--reader-font-size', prefs.fontSize + 'px');
		reader.dataset.headings = prefs.headings ? 'on' : 'off';
		reader.dataset.width = prefs.width;
		if (sizeLabel) sizeLabel.textContent = String(prefs.fontSize);
		if (headingsButton) headingsButton.setAttribute('aria-pressed', String(prefs.headings));
		if (widthButton && widthLabel) {
			widthLabel.textContent = widthButton.getAttribute('data-width-' + prefs.width) || prefs.width;
		}
	}

	function update(change) {
		change();
		storage.set(READER_PREFS_KEY, JSON.stringify(prefs));
		apply();
	}

	apply();

	var projectKey = reader.dataset.projectKey;
	var chapter = reader.dataset.chapter;
	if (projectKey && chapter) storage.set(lastChapterKey(projectKey), chapter);

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

	var actions = {
		smaller: function () {
			update(function () { prefs.fontSize = stepFontSize(prefs.fontSize, -1); });
		},
		larger: function () {
			update(function () { prefs.fontSize = stepFontSize(prefs.fontSize, 1); });
		},
		'reset-size': function () {
			update(function () { prefs.fontSize = READER_DEFAULT_FONT_SIZE; });
		},
		headings: function () {
			update(function () { prefs.headings = !prefs.headings; });
		},
		width: function () {
			update(function () { prefs.width = nextWidth(prefs.width); });
		},
		contents: openContents,
		'close-contents': closeContents,
	};

	document.addEventListener('click', function (event) {
		var button = event.target.closest('[data-reader-action]');
		if (button && actions[button.dataset.readerAction]) {
			actions[button.dataset.readerAction]();
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
