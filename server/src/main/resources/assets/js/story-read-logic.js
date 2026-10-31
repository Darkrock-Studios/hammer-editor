/**
 * Pure reading-preference logic for the author's chapter reader: no DOM, no storage. Kept
 * DOM-free so it can be unit-tested under Node while still loading as a plain browser <script>
 * (see the dual export at the end). story-read.js supplies the browser wiring.
 *
 * The font sizes mirror the app's reader (EDITOR_TEXT_SIZES), with the web prose's 18px as the
 * default. Scene headings start hidden, as in the app.
 */
var READER_FONT_SIZES = [12, 14, 16, 18, 20, 24, 28, 32];
var READER_DEFAULT_FONT_SIZE = 18;
var READER_WIDTHS = ['narrow', 'medium', 'wide'];
var READER_DEFAULT_WIDTH = 'medium';
var READER_PREFS_KEY = 'hammer.reader.prefs';

function defaultReaderPrefs() {
	return {fontSize: READER_DEFAULT_FONT_SIZE, headings: false, width: READER_DEFAULT_WIDTH};
}

/** The nearest supported size to [size], so a stored value from an older step list still lands. */
function nearestFontSize(size) {
	var best = READER_FONT_SIZES[0];
	for (var i = 0; i < READER_FONT_SIZES.length; i++) {
		if (Math.abs(READER_FONT_SIZES[i] - size) < Math.abs(best - size)) best = READER_FONT_SIZES[i];
	}
	return best;
}

/** Reads stored prefs, falling back field by field on anything missing or malformed. */
function parseReaderPrefs(raw) {
	var prefs = defaultReaderPrefs();
	var stored;
	try {
		stored = JSON.parse(raw);
	} catch (e) {
		return prefs;
	}
	if (!stored || typeof stored !== 'object') return prefs;

	if (typeof stored.fontSize === 'number' && isFinite(stored.fontSize)) {
		prefs.fontSize = nearestFontSize(stored.fontSize);
	}
	if (typeof stored.headings === 'boolean') prefs.headings = stored.headings;
	if (READER_WIDTHS.indexOf(stored.width) !== -1) prefs.width = stored.width;
	return prefs;
}

/** One step up (direction > 0) or down from [size], held at the ends of the list. */
function stepFontSize(size, direction) {
	var index = READER_FONT_SIZES.indexOf(nearestFontSize(size));
	var next = Math.min(READER_FONT_SIZES.length - 1, Math.max(0, index + (direction > 0 ? 1 : -1)));
	return READER_FONT_SIZES[next];
}

/** The width after [width], wrapping from the widest back to the narrowest. */
function nextWidth(width) {
	var index = READER_WIDTHS.indexOf(width);
	return READER_WIDTHS[(index + 1) % READER_WIDTHS.length];
}

/** Storage key for the chapter an author last opened in project [projectKey]. */
function lastChapterKey(projectKey) {
	return 'hammer.reader.lastChapter:' + projectKey;
}

/** A stored last-read chapter as a positive integer, or null. */
function parseLastChapter(raw) {
	var chapter = parseInt(raw, 10);
	return chapter > 0 ? chapter : null;
}

/**
 * Whether a key press should turn the chapter: plain arrow keys only, never while typing or while
 * a modifier is held (those belong to the browser).
 */
function isChapterKey(event, targetIsEditable) {
	if (targetIsEditable) return false;
	if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return false;
	return event.key === 'ArrowLeft' || event.key === 'ArrowRight';
}

if (typeof module !== 'undefined' && module.exports) {
	module.exports = {
		READER_FONT_SIZES: READER_FONT_SIZES,
		READER_DEFAULT_FONT_SIZE: READER_DEFAULT_FONT_SIZE,
		READER_PREFS_KEY: READER_PREFS_KEY,
		defaultReaderPrefs: defaultReaderPrefs,
		parseReaderPrefs: parseReaderPrefs,
		stepFontSize: stepFontSize,
		nextWidth: nextWidth,
		lastChapterKey: lastChapterKey,
		parseLastChapter: parseLastChapter,
		isChapterKey: isChapterKey,
	};
}
