/**
 * Reading preferences shared by the author's chapter reader and the published story page: text
 * size everywhere, scene headings and column width where the page offers them. Applies the stored
 * prefs to the [data-reader-root] element and wires whichever data-reader-action buttons the page
 * has. The rules live in story-read-logic.js (DOM-free, unit-tested), loaded before this.
 */
/* global READER_PREFS_KEY, READER_DEFAULT_FONT_SIZE, parseReaderPrefs, stepFontSize, nextWidth */

// localStorage throws in some private modes and with storage disabled; the reader then works with
// defaults and simply forgets.
var readerStorage = {
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

(function () {
	var root = document.querySelector('[data-reader-root]');
	if (!root) return;

	var prefs = parseReaderPrefs(readerStorage.get(READER_PREFS_KEY));
	var sizeLabel = root.querySelector('[data-reader-size-label]');
	var headingsButton = root.querySelector('[data-reader-action="headings"]');
	var widthButton = root.querySelector('[data-reader-action="width"]');
	var widthLabel = root.querySelector('[data-reader-width-label]');

	function apply() {
		root.style.setProperty('--reader-font-size', prefs.fontSize + 'px');
		root.dataset.headings = prefs.headings ? 'on' : 'off';
		root.dataset.width = prefs.width;
		if (sizeLabel) sizeLabel.textContent = String(prefs.fontSize);
		if (headingsButton) headingsButton.setAttribute('aria-pressed', String(prefs.headings));
		if (widthButton && widthLabel) {
			widthLabel.textContent = widthButton.getAttribute('data-width-' + prefs.width) || prefs.width;
		}
	}

	function update(change) {
		change();
		readerStorage.set(READER_PREFS_KEY, JSON.stringify(prefs));
		apply();
	}

	apply();

	// A Map, so a data-reader-action value can only name one of these, never an inherited property.
	var actions = new Map([
		['smaller', function () {
			update(function () { prefs.fontSize = stepFontSize(prefs.fontSize, -1); });
		}],
		['larger', function () {
			update(function () { prefs.fontSize = stepFontSize(prefs.fontSize, 1); });
		}],
		['reset-size', function () {
			update(function () { prefs.fontSize = READER_DEFAULT_FONT_SIZE; });
		}],
		['headings', function () {
			update(function () { prefs.headings = !prefs.headings; });
		}],
		['width', function () {
			update(function () { prefs.width = nextWidth(prefs.width); });
		}],
	]);

	document.addEventListener('click', function (event) {
		var button = event.target.closest('[data-reader-action]');
		var action = button && actions.get(button.dataset.readerAction);
		if (action) action();
	});
})();
