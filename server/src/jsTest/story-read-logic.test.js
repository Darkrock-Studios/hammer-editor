const {test} = require('node:test');
const assert = require('node:assert/strict');
const {
	READER_DEFAULT_FONT_SIZE,
	defaultReaderPrefs,
	parseReaderPrefs,
	stepFontSize,
	nextWidth,
	lastChapterKey,
	parseLastChapter,
	isChapterKey,
} = require('../main/resources/assets/js/story-read-logic.js');

test('missing or broken prefs fall back to the defaults', () => {
	assert.deepEqual(parseReaderPrefs(null), defaultReaderPrefs());
	assert.deepEqual(parseReaderPrefs('not json'), defaultReaderPrefs());
	assert.deepEqual(parseReaderPrefs('"a string"'), defaultReaderPrefs());
});

test('stored prefs are kept', () => {
	const prefs = parseReaderPrefs(JSON.stringify({fontSize: 24, headings: true, width: 'wide'}));
	assert.deepEqual(prefs, {fontSize: 24, headings: true, width: 'wide'});
});

test('each malformed field falls back on its own', () => {
	const prefs = parseReaderPrefs(JSON.stringify({fontSize: 'big', headings: 'yes', width: 'huge'}));
	assert.deepEqual(prefs, defaultReaderPrefs());
	const partial = parseReaderPrefs(JSON.stringify({headings: true}));
	assert.equal(partial.headings, true);
	assert.equal(partial.fontSize, READER_DEFAULT_FONT_SIZE);
});

test('an unsupported stored size snaps to the nearest step', () => {
	assert.equal(parseReaderPrefs(JSON.stringify({fontSize: 17})).fontSize, 16);
	assert.equal(parseReaderPrefs(JSON.stringify({fontSize: 1000})).fontSize, 32);
	assert.equal(parseReaderPrefs(JSON.stringify({fontSize: -4})).fontSize, 12);
});

test('font size steps and holds at the ends', () => {
	assert.equal(stepFontSize(18, 1), 20);
	assert.equal(stepFontSize(18, -1), 16);
	assert.equal(stepFontSize(32, 1), 32);
	assert.equal(stepFontSize(12, -1), 12);
});

test('width cycles narrow, medium, wide', () => {
	assert.equal(nextWidth('narrow'), 'medium');
	assert.equal(nextWidth('medium'), 'wide');
	assert.equal(nextWidth('wide'), 'narrow');
});

test('last chapter is scoped per project and must be positive', () => {
	assert.notEqual(lastChapterKey('a'), lastChapterKey('b'));
	assert.equal(parseLastChapter('3'), 3);
	assert.equal(parseLastChapter('0'), null);
	assert.equal(parseLastChapter(null), null);
	assert.equal(parseLastChapter('x'), null);
});

test('only plain arrow keys away from focused controls turn the chapter', () => {
	assert.equal(isChapterKey({key: 'ArrowRight'}, false), true);
	assert.equal(isChapterKey({key: 'ArrowLeft'}, false), true);
	assert.equal(isChapterKey({key: 'ArrowRight'}, true), false);
	assert.equal(isChapterKey({key: 'ArrowRight', altKey: true}, false), false);
	assert.equal(isChapterKey({key: 'ArrowLeft', metaKey: true}, false), false);
	assert.equal(isChapterKey({key: 'ArrowUp'}, false), false);
});
