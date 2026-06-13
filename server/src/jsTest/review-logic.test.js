const { test } = require('node:test');
const assert = require('node:assert/strict');
const logic = require('../main/resources/assets/js/review-logic.js');

test('computeSegments splits text around a single suggestion', () => {
	const text = 'It flagged the planet as non-viable and moved on.';
	const s = { type: 'reword', start: 25, end: 35, replacement: 'nonviable' };
	const segs = logic.computeSegments(text, [s]);
	assert.deepEqual(segs.map((x) => x.text), [
		'It flagged the planet as ',
		'non-viable',
		' and moved on.',
	]);
	assert.equal(segs[1].suggestion, s);
});

test('computeSegments orders multiple suggestions and keeps plain tails', () => {
	const text = 'abcdefghij';
	const segs = logic.computeSegments(text, [
		{ start: 6, end: 8 },
		{ start: 1, end: 3 },
	]);
	assert.deepEqual(segs.map((x) => x.text), ['a', 'bc', 'def', 'gh', 'ij']);
});

test('rangesOverlap treats touching ranges as non-overlapping and carets as never overlapping', () => {
	assert.equal(logic.rangesOverlap(0, 5, 5, 9), false); // adjacent
	assert.equal(logic.rangesOverlap(0, 5, 4, 9), true); // overlap
	assert.equal(logic.rangesOverlap(3, 3, 0, 9), false); // caret inside
});

test('smartSpaceInsert pads a word after a non-space but not punctuation', () => {
	const text = 'one two';
	assert.equal(logic.smartSpaceInsert(text, 3, 'and'), ' and'); // between words
	assert.equal(logic.smartSpaceInsert(text, 3, ','), ','); // punctuation, no pad
	assert.equal(logic.smartSpaceInsert(text, 0, 'New'), 'New'); // start of para
	assert.equal(logic.smartSpaceInsert(text, 4, 'x'), 'x'); // after a space already
});

test('applyAccepted applies only accepted edits, right-to-left', () => {
	const text = 'The quick brown fox';
	const suggestions = [
		{ type: 'reword', start: 4, end: 9, replacement: 'slow', status: 'accepted' }, // quick -> slow
		{ type: 'delete', start: 10, end: 16, status: 'accepted' }, // remove "brown "
		{ type: 'reword', start: 16, end: 19, replacement: 'cat', status: 'rejected' }, // fox -> cat (rejected)
	];
	assert.equal(logic.applyAccepted(text, suggestions), 'The slow fox');
});

test('applyAccepted inserts at a caret and ignores comments', () => {
	const text = 'Power reserves nominal.';
	const suggestions = [
		{ type: 'insert', start: 23, end: 23, replacement: ' Systems green.', status: 'accepted' },
		{ type: 'comment', start: 0, end: 5, status: 'accepted' },
	];
	assert.equal(logic.applyAccepted(text, suggestions), 'Power reserves nominal. Systems green.');
});

test('strikeSlope is deterministic, varies by seed, and never lands flat', () => {
	assert.equal(logic.strikeSlope('s1'), logic.strikeSlope('s1')); // stable
	const slopes = new Set();
	for (let i = 0; i < 50; i++) slopes.add(logic.strikeSlope('sugg-' + i));
	assert.ok(slopes.size > 20, 'expected varied slopes across seeds');
	for (const v of slopes) {
		assert.ok(Math.abs(v) >= 1.4 && Math.abs(v) <= 3.6, 'slope in range: ' + v);
	}
	// both directions occur across seeds
	const someUp = [...Array(50).keys()].some((i) => logic.strikeSlope('u' + i) > 0);
	const someDown = [...Array(50).keys()].some((i) => logic.strikeSlope('u' + i) < 0);
	assert.ok(someUp && someDown, 'expected both up and down strikes');
});

test('buildStrikeBackground embeds the colour and is a data url', () => {
	const bg = logic.buildStrikeBackground('#b91c1c', 'abc');
	assert.match(bg, /^url\("data:image\/svg\+xml,/);
	assert.ok(decodeURIComponent(bg).includes('#b91c1c'));
	assert.equal(bg, logic.buildStrikeBackground('#b91c1c', 'abc')); // deterministic
});
