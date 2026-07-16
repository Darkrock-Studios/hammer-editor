/**
 * Pure dwell-tracking logic for the story-reader beacon — no DOM, no globals
 * beyond this factory. Kept DOM-free so it can be unit-tested under Node while
 * still loading as a plain browser <script> (see the dual export at the end),
 * mirroring review-logic.js. story-reader.js supplies the browser wiring
 * (timers, the Visibility API, sessionStorage, navigator.sendBeacon).
 *
 * A tracker accrues visible dwell time toward a threshold and carries the
 * running total across page turns through an injected best-effort storage
 * (sessionStorage in the browser). Both the clock and the storage are injected
 * so the accumulation is deterministic under test.
 *
 * Storage contract: get(key) returns the stored string or null; set(key, value)
 * persists it. Both must be best-effort — never throw — so the browser wiring
 * can degrade to a per-page timer when sessionStorage is unavailable.
 */
function createDwellTracker(options) {
	var thresholdMs = options.thresholdMs;
	var now = options.now;          // () => epoch millis
	var storage = options.storage;  // { get(key): string|null, set(key, value): void }
	var storyKey = options.storyKey;

	// Only the ?page=N query varies between pages of one story, so the pathname
	// (passed in as storyKey) scopes both keys to the story across page turns.
	var dwellStorageKey = 'hammer.readDwell:' + storyKey;
	var sentStorageKey = 'hammer.readSent:' + storyKey;

	var dwellMs = parseInt(storage.get(dwellStorageKey), 10) || 0;
	var sent = storage.get(sentStorageKey) === '1';
	var lastResume = null;

	// Fold the time since the last resume into the running total and persist it,
	// so the next page (a full reload) picks up where this one left off. Keeps
	// the clock running — call pause() to also stop accruing.
	function accrue() {
		if (lastResume === null) return;
		var t = now();
		dwellMs += t - lastResume;
		lastResume = t;
		storage.set(dwellStorageKey, String(dwellMs));
	}

	return {
		// True once this story has been counted in this tab. The caller must then
		// stop accruing and never beacon again — this survives page reloads via
		// storage, so a page-turner is only ever counted once.
		alreadySent: function () {
			return sent;
		},

		// Begin (or resume) accruing dwell from now. Idempotent while running, so
		// a redundant visibilitychange can't reset the reference point.
		resume: function () {
			if (lastResume === null) lastResume = now();
		},

		// Accrue elapsed time and stop the clock (tab hidden / navigating away).
		pause: function () {
			accrue();
			lastResume = null;
		},

		accrue: accrue,

		// Accrue, then report whether the visitor has now dwelt long enough to count.
		thresholdReached: function () {
			accrue();
			return dwellMs >= thresholdMs;
		},

		// Record that the beacon has fired so no later page or tab refocus re-sends it.
		markSent: function () {
			sent = true;
			storage.set(sentStorageKey, '1');
		},

		// Accumulated visible dwell in ms (inspection/test aid).
		dwellMs: function () {
			return dwellMs;
		},
	};
}

if (typeof module !== 'undefined' && module.exports) {
	module.exports = {
		createDwellTracker: createDwellTracker,
	};
}
