/**
 * Best-effort "reader" beacon for published / shared stories.
 *
 * The server no longer counts a read the moment the page loads — that counts
 * every drive-by click, including people who open the story and close it a
 * second later. Instead we wait until the visitor has actually spent a short
 * dwell time on the page, then fire a single beacon to POST .../read; the
 * server records the unique reader only when that beacon arrives.
 *
 * The dwell timer only accrues while the tab is visible, so a story opened in a
 * background tab and never looked at doesn't count either. This is a heuristic,
 * not a guarantee — it just filters the obvious bounces.
 */
(function () {
	// Minimum visible time on the page before we count the visit as a read.
	var DWELL_THRESHOLD_MS = 10000;

	var sent = false;
	var dwellMs = 0;
	var lastResume = null;
	var timer = null;

	function sendBeacon() {
		if (sent) return;
		sent = true;
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

	function tick() {
		if (sent || lastResume === null) return;
		var now = Date.now();
		dwellMs += now - lastResume;
		lastResume = now;
		if (dwellMs >= DWELL_THRESHOLD_MS) {
			sendBeacon();
		}
	}

	function startTimer() {
		if (sent || timer !== null) return;
		lastResume = Date.now();
		timer = setInterval(tick, 1000);
	}

	function stopTimer() {
		if (timer !== null) {
			// Fold the in-progress interval into the accumulated total before pausing.
			if (lastResume !== null) {
				dwellMs += Date.now() - lastResume;
				lastResume = null;
			}
			clearInterval(timer);
			timer = null;
		}
	}

	// Only accrue dwell time while the tab is actually in the foreground.
	document.addEventListener('visibilitychange', function () {
		if (document.hidden) {
			stopTimer();
		} else {
			startTimer();
		}
	});

	if (!document.hidden) {
		startTimer();
	}
})();
