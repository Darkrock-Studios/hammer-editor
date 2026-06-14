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

test('rangesOverlap treats touching ranges as non-overlapping', () => {
	assert.equal(logic.rangesOverlap(0, 5, 5, 9), false); // adjacent
	assert.equal(logic.rangesOverlap(0, 5, 4, 9), true); // overlap
});

test('rangesOverlap counts a caret strictly inside a range as a collision', () => {
	assert.equal(logic.rangesOverlap(3, 3, 0, 9), true); // caret inside range
	assert.equal(logic.rangesOverlap(0, 9, 3, 3), true); // range spans caret
	assert.equal(logic.rangesOverlap(0, 0, 0, 9), false); // caret at range start
	assert.equal(logic.rangesOverlap(9, 9, 0, 9), false); // caret at range end
	assert.equal(logic.rangesOverlap(3, 3, 3, 3), false); // caret on caret
});

test('smartSpaceInsert pads a word on both sides but never around punctuation', () => {
	const text = 'one two';
	assert.equal(logic.smartSpaceInsert(text, 3, 'and'), ' and'); // after a word, before a space
	assert.equal(logic.smartSpaceInsert(text, 3, ','), ','); // punctuation, no pad
	assert.equal(logic.smartSpaceInsert(text, 0, 'New'), 'New '); // start of para, before a word
	assert.equal(logic.smartSpaceInsert(text, 4, 'x'), 'x '); // after a space, before a word
	assert.equal(logic.smartSpaceInsert(text, 7, 'more'), ' more'); // end of para
	assert.equal(logic.smartSpaceInsert('one, two', 3, 'word'), ' word'); // before a comma, no trail
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

test('applyAccepted commits same-position carets in display order', () => {
	const text = 'abcdef';
	const suggestions = [
		{ id: 1, type: 'insert', start: 3, end: 3, replacement: 'A', status: 'accepted' },
		{ id: 2, type: 'insert', start: 3, end: 3, replacement: 'B', status: 'accepted' },
	];
	// computeSegments renders [A, B] at the caret; the splice must agree.
	const segs = logic.computeSegments(text, suggestions);
	assert.deepEqual(segs.filter((s) => s.suggestion).map((s) => s.suggestion.id), [1, 2]);
	assert.equal(logic.applyAccepted(text, suggestions), 'abcABdef');
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

test('parseInlineMarkdown renders bold and italic without markers, offsets stay in source space', () => {
	const text = '**Down the Rabbit-Hole** and *italic* tail';
	const runs = logic.parseInlineMarkdown(text);
	assert.deepEqual(runs.map((r) => [r.text, r.srcStart, r.bold, r.italic]), [
		['Down the Rabbit-Hole', 2, true, false],
		[' and ', 24, false, false],
		['italic', 30, false, true],
		[' tail', 37, false, false],
	]);
	// every run's text is literally present at its claimed source position
	for (const r of runs) {
		assert.equal(text.slice(r.srcStart, r.srcStart + r.text.length), r.text);
	}
});

test('parseInlineMarkdown handles the Alice sample with quoted italics', () => {
	const text = 'no pictures in it, "*and what is the use of a book,*" thought Alice';
	const runs = logic.parseInlineMarkdown(text);
	const italic = runs.find((r) => r.italic);
	assert.equal(italic.text, 'and what is the use of a book,');
	assert.equal(text.slice(italic.srcStart, italic.srcStart + italic.text.length), italic.text);
	// surrounding quotes stay plain
	assert.equal(runs[0].text, 'no pictures in it, "');
	assert.equal(runs[0].bold || runs[0].italic, false);
});

test('parseInlineMarkdown supports nesting and leaves unmatched or intraword markers literal', () => {
	const nested = logic.parseInlineMarkdown('**bold *both* bold**');
	assert.deepEqual(nested.map((r) => [r.text, r.bold, r.italic]), [
		['bold ', true, false],
		['both', true, true],
		[' bold', true, false],
	]);

	const literal = logic.parseInlineMarkdown('a * b and snake_case_name and 2*3');
	assert.equal(literal.length, 1);
	assert.equal(literal[0].text, 'a * b and snake_case_name and 2*3');

	const unclosed = logic.parseInlineMarkdown('an *unclosed marker');
	assert.equal(unclosed.map((r) => r.text).join(''), 'an *unclosed marker');
});

test('parseInlineMarkdown unescapes backslash-escaped punctuation, keeping source offsets', () => {
	const text = 'Down the Rabbit\\-Hole \\(here\\) What\\!';
	const runs = logic.parseInlineMarkdown(text);
	assert.equal(runs.map((r) => r.text).join(''), 'Down the Rabbit-Hole (here) What!');
	// every run's text is literally present at its claimed source position
	for (const r of runs) {
		assert.equal(text.slice(r.srcStart, r.srcStart + r.text.length), r.text);
	}
});

test('parseInlineMarkdown treats an escaped emphasis marker as literal', () => {
	const runs = logic.parseInlineMarkdown('not \\*bold\\* here');
	assert.equal(runs.map((r) => r.text).join(''), 'not *bold* here');
	assert.equal(runs.some((r) => r.bold || r.italic), false);
});

test('parseInlineMarkdown keeps a lone backslash literal but collapses an escaped backslash', () => {
	const text = 'a\\b and back\\\\slash';
	const runs = logic.parseInlineMarkdown(text);
	assert.equal(runs.map((r) => r.text).join(''), 'a\\b and back\\slash');
	for (const r of runs) {
		assert.equal(text.slice(r.srcStart, r.srcStart + r.text.length), r.text);
	}
});

test('runsForRange clips runs to a source range and skips marker gaps', () => {
	const text = 'plain **bold** end';
	const runs = logic.parseInlineMarkdown(text);
	// Range covering "n **bo" in source space: plain tail + opening marker + "bo"
	const clipped = logic.runsForRange(runs, 4, 10);
	assert.deepEqual(clipped.map((r) => [r.text, r.srcStart, r.bold]), [
		['n ', 4, false],
		['bo', 8, true],
	]);
});

test('buildStrikeBackground embeds the colour and is a data url', () => {
	const bg = logic.buildStrikeBackground('#b91c1c', 'abc');
	assert.match(bg, /^url\("data:image\/svg\+xml,/);
	assert.ok(decodeURIComponent(bg).includes('#b91c1c'));
	assert.equal(bg, logic.buildStrikeBackground('#b91c1c', 'abc')); // deterministic
});
