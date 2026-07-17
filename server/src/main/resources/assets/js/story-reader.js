/**
 * Best-effort "reader" beacon for published / shared stories — browser wiring.
 *
 * The server no longer counts a read the moment the page loads — that counts
 * every drive-by click, including people who open the story and close it a
 * second later. Instead we wait until the visitor has actually spent a short
 * dwell time on the page, then fire a single beacon to POST .../read; the
 * server records the unique reader only when that beacon arrives.
 *
 * Two refinements keep the heuristic honest:
 *  - The dwell timer only accrues while the tab is visible, so a story opened in
 *    a background tab and never looked at doesn't count.
 *  - Dwell accumulates ACROSS pages of a multi-page story. Pagination is a full
 *    page navigation, so each page reloads this script; the running total lives
 *    in sessionStorage (per-tab, never sent to the server) so a reader who
 *    spends a few seconds on each of several pages still crosses the threshold.
 *
 * The dwell math lives in story-reader-logic.js (DOM-free, unit-tested);
 * createDwellTracker is a global here, loaded as page_pre_script before this
 * file. This file is just the timers, Visibility API, storage, and beacon.
 *
 * This is a heuristic, not a guarantee — it just filters the obvious bounces.
 */
(function () {
	// Minimum visible time on the page before we count the visit as a read.
	var DWELL_THRESHOLD_MS = 10000;

	// sessionStorage can throw (private-mode quotas, disabled storage). Degrade to
	// a per-page timer rather than break the page: dwell just won't carry across
	// pages, which is the same as having no cross-page refinement at all.
	var storage = {
		get: function (key) {
			try {
				return sessionStorage.getItem(key);
			} catch (e) {
				return null;
			}
		},
		set: function (key, value) {
			try {
				sessionStorage.setItem(key, value);
			} catch (e) {
				// Best-effort only.
			}
		},
	};

	var tracker = createDwellTracker({
		thresholdMs: DWELL_THRESHOLD_MS,
		now: function () {
			return Date.now();
		},
		storage: storage,
		storyKey: window.location.pathname,
	});

	// Already counted this story in this tab — nothing more to do.
	if (tracker.alreadySent()) return;

	var timer = null;

	function fireBeacon() {
		// Beacon back to the same story URL (carrying ?p=... for private shares and
		// any &page=...). The server re-resolves access from these exactly as the
		// GET did, so the beacon can't record anything the visitor couldn't load.
		var url = window.location.pathname + '/read' + window.location.search;

		try {
			if (navigator.sendBeacon) {
				// sendBeacon survives the page being closed and still sends cookies,
				// so the author-skip session check keeps working.
				navigator.sendBeacon(url);
			} else {
				fetch(url, {method: 'POST', credentials: 'same-origin', keepalive: true});
			}
		} catch (e) {
			// Best-effort telemetry: if the beacon can't be sent, we simply don't
			// count this reader. Never disrupt the reading experience.
		}
	}

	function tick() {
		if (tracker.thresholdReached()) {
			tracker.markSent();
			stopTimer();
			fireBeacon();
		}
	}

	function startTimer() {
		// Once sent, never resume — a later tab refocus must not re-fire the beacon.
		if (timer !== null || tracker.alreadySent()) return;
		tracker.resume();
		timer = setInterval(tick, 1000);
	}

	function stopTimer() {
		if (timer !== null) {
			clearInterval(timer);
			timer = null;
		}
		tracker.pause();
	}

	// Only accrue dwell time while the tab is actually in the foreground.
	document.addEventListener('visibilitychange', function () {
		if (document.hidden) {
			stopTimer();
		} else {
			startTimer();
		}
	});

	// Persist the in-progress dwell when navigating away (e.g. clicking "next
	// page") so it isn't lost between the tick interval and the page unload.
	window.addEventListener('pagehide', function () {
		tracker.accrue();
	});

	if (!document.hidden) {
		startTimer();
	}
})();
