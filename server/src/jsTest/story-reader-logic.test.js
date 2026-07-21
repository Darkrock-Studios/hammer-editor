const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createDwellTracker } = require('../main/resources/assets/js/story-reader-logic.js');

const STORY_KEY = '/a/jane/my-story-abc123';

// A controllable clock plus in-memory storage, so dwell accumulation is fully
// deterministic — no real time, no sessionStorage. `advance` moves the clock;
// `store` is inspectable so tests can assert what got persisted across pages.
function harness(initialStore) {
	let clock = 0;
	const store = Object.assign({}, initialStore);
	return {
		advance(ms) { clock += ms; },
		now() { return clock; },
		store,
		storage: {
			get(key) { return key in store ? store[key] : null; },
			set(key, value) { store[key] = String(value); },
		},
	};
}

function makeTracker(h, overrides) {
	return createDwellTracker(Object.assign({
		thresholdMs: 10000,
		now: h.now,
		storage: h.storage,
		storyKey: STORY_KEY,
	}, overrides));
}

test('the threshold is reached only after the full dwell time is visible', () => {
	const h = harness();
	const tr = makeTracker(h);
	tr.resume();

	h.advance(9000);
	assert.equal(tr.thresholdReached(), false); // 9s < 10s

	h.advance(1000);
	assert.equal(tr.thresholdReached(), true); // 10s hits the threshold
});

test('time while paused (hidden tab) does not count toward dwell', () => {
	const h = harness();
	const tr = makeTracker(h);

	tr.resume();
	h.advance(5000);
	tr.pause(); // tab hidden

	h.advance(3600000); // an hour in the background — must not count
	tr.resume(); // tab visible again

	h.advance(4000);
	assert.equal(tr.thresholdReached(), false); // accrues, then checks
	assert.equal(tr.dwellMs(), 9000); // only the 5s + 4s of visible time

	h.advance(1000);
	assert.equal(tr.thresholdReached(), true);
});

test('dwell carries across page turns through storage', () => {
	const h = harness();

	// Page 1: reader spends 6s, then navigates (pagehide persists the total).
	const page1 = makeTracker(h);
	page1.resume();
	h.advance(6000);
	page1.accrue();
	assert.equal(page1.thresholdReached(), false);

	// Page 2: a brand-new tracker (full reload) resumes from the persisted 6s.
	const page2 = makeTracker(h);
	page2.resume();
	h.advance(5000);
	assert.equal(page2.thresholdReached(), true); // 6s + 5s crosses 10s
});

test('a persisted over-threshold dwell is reached on the first check after resume', () => {
	const h = harness({ ['hammer.readDwell:' + STORY_KEY]: '9500' });
	const tr = makeTracker(h);

	tr.resume();
	h.advance(1000);
	assert.equal(tr.thresholdReached(), true);
});

test('markSent persists and is seen by a later tracker for the same story', () => {
	const h = harness();

	const first = makeTracker(h);
	assert.equal(first.alreadySent(), false);
	first.markSent();
	assert.equal(first.alreadySent(), true);

	// A later page reload constructs a fresh tracker and sees it already counted.
	const later = makeTracker(h);
	assert.equal(later.alreadySent(), true);
});

test('dwell is scoped per story path so a different story starts fresh', () => {
	const h = harness();

	const storyA = makeTracker(h, { storyKey: '/a/jane/story-a' });
	storyA.resume();
	h.advance(8000);
	storyA.accrue();

	const storyB = makeTracker(h, { storyKey: '/a/jane/story-b' });
	assert.equal(storyB.dwellMs(), 0);
});
