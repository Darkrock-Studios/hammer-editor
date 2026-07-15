/**
 * Best-effort "reader" beacon for published / shared stories.
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
 *    page navigation, so each page reloads this script; we carry the running
 *    total in sessionStorage (per-tab, never sent to the server, no crypto or
 *    backend involved) keyed by the story path, so a reader who spends a few
 *    seconds on each of several pages still crosses the threshold. Without this
 *    a page-turner reading a long serialized story would never be counted.
 *
 * This is a heuristic, not a guarantee — it just filters the obvious bounces.
 */
(function () {
	// Minimum visible time on the page before we count the visit as a read.
	var DWELL_THRESHOLD_MS = 10000;

	// The story path is /a/{penName}/{projectName}; only the ?page=N query varies
	// between pages, so the pathname identifies the story across page turns.
	var storyKey = window.location.pathname;
	var DWELL_KEY = 'hammer.readDwell:' + storyKey;
	var SENT_KEY = 'hammer.readSent:' + storyKey;

	// sessionStorage can throw (private-mode quotas, disabled storage). Degrade to
	// a per-page timer rather than break the page: dwell just won't carry across
	// pages, which is the same as having no refinement at all.
	function storageGet(key) {
		try {
			return sessionStorage.getItem(key);
		} catch (e) {
			return null;
		}
	}

	function storageSet(key, value) {
		try {
			sessionStorage.setItem(key, value);
		} catch (e) {
			// Best-effort only.
		}
	}

	// Already counted this story in this tab — nothing more to do.
	if (storageGet(SENT_KEY) === '1') return;

	var dwellMs = parseInt(storageGet(DWELL_KEY), 10) || 0;
	var lastResume = null;
	var timer = null;

	function sendBeacon() {
		storageSet(SENT_KEY, '1');
		stopTimer();

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

	// Fold the time since the last resume into the running total and persist it,
	// so the next page (a full reload) picks up where this one left off.
	function accrue() {
		if (lastResume === null) return;
		var now = Date.now();
		dwellMs += now - lastResume;
		lastResume = now;
		storageSet(DWELL_KEY, String(dwellMs));
	}

	function tick() {
		accrue();
		if (dwellMs >= DWELL_THRESHOLD_MS) {
			sendBeacon();
		}
	}

	function startTimer() {
		if (timer !== null) return;
		lastResume = Date.now();
		timer = setInterval(tick, 1000);
	}

	function stopTimer() {
		if (timer !== null) {
			accrue();
			clearInterval(timer);
			timer = null;
		}
		lastResume = null;
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
	window.addEventListener('pagehide', accrue);

	if (!document.hidden) {
		startTimer();
	}
})();
