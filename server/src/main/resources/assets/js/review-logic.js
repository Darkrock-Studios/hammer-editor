/**
 * Pure suggestion logic for the reviewer editor — no DOM, no globals beyond
 * these functions. Kept DOM-free so it can be unit-tested under Node while
 * still loading as a plain browser <script> (see the dual export at the end).
 *
 * A suggestion is: { type, para, start, end, replacement, reason }
 *   - type: 'delete' | 'reword' | 'insert' | 'comment'
 *   - start/end: character offsets into the *original* paragraph text
 *   - insert: start === end (a caret position); replacement is the new text
 */

/**
 * Split a paragraph into render segments: plain runs interleaved with the
 * suggestions that cover them. Overlapping suggestions are not expected
 * (creation rejects them), but cur clamps defensively.
 * @returns {Array<{text:string, suggestion?:object}>}
 */
function computeSegments(text, suggestions) {
	const sorted = suggestions.slice().sort(function (a, b) {
		return a.start - b.start || a.end - b.end;
	});
	const segments = [];
	let cur = 0;
	for (const s of sorted) {
		if (s.start > cur) segments.push({ text: text.slice(cur, s.start) });
		segments.push({ text: text.slice(s.start, s.end), suggestion: s });
		cur = Math.max(cur, s.end);
	}
	if (cur < text.length) segments.push({ text: text.slice(cur) });
	return segments;
}

/**
 * True if [aStart,aEnd) and [bStart,bEnd) collide. A caret (zero-width) collides
 * with a range only when strictly inside it — a spanning edit would swallow the
 * insertion at apply time. Two carets never collide.
 */
function rangesOverlap(aStart, aEnd, bStart, bEnd) {
	const aCaret = aStart === aEnd;
	const bCaret = bStart === bEnd;
	if (aCaret && bCaret) return false;
	if (aCaret) return aStart > bStart && aStart < bEnd;
	if (bCaret) return bStart > aStart && bStart < aEnd;
	return !(aEnd <= bStart || aStart >= bEnd);
}

/** True if a new range overlaps any existing non-caret suggestion in the same paragraph. */
function overlapsAny(start, end, suggestions) {
	return suggestions.some(function (s) {
		return rangesOverlap(start, end, s.start, s.end);
	});
}

/**
 * Pad an inserted string with spaces so it reads as its own word: a leading
 * space when it follows a non-space, a trailing one when it abuts the next
 * word — but never around punctuation.
 */
function smartSpaceInsert(paraText, start, replacement) {
	if (!replacement) return replacement;
	const punct = ',.;:!?—’”)…-';
	const prev = start > 0 ? paraText[start - 1] : '';
	const first = replacement[0];
	const needsLead = !/\s/.test(first) && punct.indexOf(first) === -1 && prev && !/\s/.test(prev);
	const next = paraText[start] || '';
	const last = replacement[replacement.length - 1];
	const needsTrail = next && !/\s/.test(next) && punct.indexOf(next) === -1 &&
		!/\s/.test(last) && punct.indexOf(last) === -1;
	return (needsLead ? ' ' : '') + replacement + (needsTrail ? ' ' : '');
}

/**
 * Produce the final paragraph text after applying a set of accepted suggestions.
 * Applied right-to-left so earlier offsets stay valid. Rejected/undecided
 * suggestions (status !== 'accepted') are left out. Comments never change text.
 */
function applyAccepted(text, suggestions) {
	const accepted = suggestions
		.filter(function (s) { return s.status === 'accepted' && s.type !== 'comment'; })
		// Descending id last: same-position carets apply newest-first so the result
		// matches the left-to-right display order.
		.sort(function (a, b) { return b.start - a.start || b.end - a.end || (b.id || 0) - (a.id || 0); });
	let result = text;
	for (const s of accepted) {
		const replacement = s.type === 'delete' ? '' : (s.replacement || '');
		result = result.slice(0, s.start) + replacement + result.slice(s.end);
	}
	return result;
}

/* ===== Inline markdown (bold/italic) -> styled runs ===== */

/**
 * Parse a paragraph's inline markdown emphasis into styled display runs.
 * Supports **bold**, *italic*, __bold__, _italic_ (and nesting). Marker
 * characters are excluded from the runs, but every run remembers where its
 * text lives in the ORIGINAL source string (srcStart), so suggestion offsets
 * stay anchored in source space while the display omits the markers.
 *
 * Deliberately conservative: an emphasis only opens when the marker is
 * preceded by start/whitespace/punctuation and followed by non-whitespace,
 * and only closes after non-whitespace (so intraword snake_case and stray
 * asterisks render literally).
 *
 * CommonMark backslash escapes are honoured: a backslash before ASCII
 * punctuation is dropped and the punctuation emitted as its own run (anchored
 * at the punctuation's source offset, so the per-run source-slice invariant
 * holds), and an escaped marker can neither open nor close emphasis.
 *
 * @returns {Array<{text:string, srcStart:number, bold:boolean, italic:boolean}>}
 */
function parseInlineMarkdown(text) {
	const ESCAPABLE = '!"#$%&\'()*+,-./:;<=>?@[\\]^_`{|}~';
	const runs = [];

	function emit(str, srcStart, bold, italic) {
		if (str.length === 0) return;
		runs.push({ text: str, srcStart: srcStart, bold: bold, italic: italic });
	}

	function isFlankableBefore(ch) { return !ch || !/[\w*_]/.test(ch); }

	function findClose(marker, from, end) {
		let i = from;
		while (i < end) {
			i = text.indexOf(marker, i);
			if (i === -1 || i >= end) return -1;
			const prev = text[i - 1];
			const after = text[i + marker.length];
			// closes after non-space; for single _ require not intraword; an
			// escaped marker (\*) is literal, never a close
			if (prev && !/\s/.test(prev) && prev !== marker[0] && prev !== '\\' &&
				(marker !== '_' || !after || !/\w/.test(after))) {
				return i;
			}
			i += marker.length;
		}
		return -1;
	}

	function walk(start, end, bold, italic) {
		let plainFrom = start;
		let i = start;
		while (i < end) {
			const ch = text[i];
			if (ch === '\\' && i + 1 < end && ESCAPABLE.indexOf(text[i + 1]) !== -1) {
				emit(text.slice(plainFrom, i), plainFrom, bold, italic);
				emit(text[i + 1], i + 1, bold, italic);
				i += 2;
				plainFrom = i;
				continue;
			}
			if (ch !== '*' && ch !== '_') { i++; continue; }
			const double = text[i + 1] === ch;
			const marker = double ? ch + ch : ch;
			const contentFrom = i + marker.length;
			const openOk = isFlankableBefore(text[i - 1]) &&
				contentFrom < end && !/\s/.test(text[contentFrom]);
			if (!openOk) { i++; continue; }
			const close = findClose(marker, contentFrom + 1, end);
			if (close === -1) { i++; continue; }

			emit(text.slice(plainFrom, i), plainFrom, bold, italic);
			walk(contentFrom, close, bold || double, italic || !double);
			i = close + marker.length;
			plainFrom = i;
		}
		emit(text.slice(plainFrom, end), plainFrom, bold, italic);
	}

	walk(0, text.length, false, false);
	return runs;
}

/**
 * Clip styled runs to a source range [start,end): the display pieces for one
 * suggestion segment. Markers falling inside the range simply have no run.
 */
function runsForRange(runs, start, end) {
	const out = [];
	for (const r of runs) {
		const rEnd = r.srcStart + r.text.length;
		const s = Math.max(start, r.srcStart);
		const e = Math.min(end, rEnd);
		if (e <= s) continue;
		out.push({
			text: r.text.slice(s - r.srcStart, e - r.srcStart),
			srcStart: s,
			bold: r.bold,
			italic: r.italic,
		});
	}
	return out;
}

/* ===== Pen-ink strike rendering ===== */

/** Deterministic FNV-1a hash of a seed, as an unsigned 32-bit int. */
function hashSeed(seed) {
	const str = String(seed);
	let h = 2166136261;
	for (let i = 0; i < str.length; i++) {
		h ^= str.charCodeAt(i);
		h = Math.imul(h, 16777619);
	}
	return h >>> 0;
}

/**
 * A per-suggestion strike slope in px of vertical drift across one tile.
 * Magnitude 1.4–3.6, sign varies, derived from the seed so a given suggestion
 * always draws the same tilt but neighbours differ — and it never lands flat.
 */
function strikeSlope(seed) {
	const h = hashSeed(seed);
	const magnitude = 1.4 + (h % 1000) / 1000 * 2.2;
	const sign = ((h >> 10) & 1) ? 1 : -1;
	return Number((sign * magnitude).toFixed(2));
}

/**
 * An SVG background drawing one straight pen line at the seed's slight angle,
 * stretched across whatever box it paints (preserveAspectRatio is off).
 */
function buildStrikeBackground(color, seed) {
	const dy = strikeSlope(seed);
	const y1 = (5 - dy / 2).toFixed(2);
	const y2 = (5 + dy / 2).toFixed(2);
	const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="10" ' +
		'viewBox="0 0 100 10" preserveAspectRatio="none">' +
		'<line x1="1" y1="' + y1 + '" x2="99" y2="' + y2 +
		'" stroke="' + color + '" stroke-width="2" stroke-linecap="round"/></svg>';
	return 'url("data:image/svg+xml,' + encodeURIComponent(svg) + '")';
}

/**
 * Inline style object that strikes text with the seed's pen line; spreads onto
 * element.style. The line stretches across each box fragment, and
 * box-decoration-break: clone repaints it per wrapped line — so every physical
 * line of struck text gets exactly one continuous straight stroke.
 */
function strikeStyle(color, seed) {
	return {
		backgroundImage: buildStrikeBackground(color, seed),
		backgroundRepeat: 'no-repeat',
		backgroundPosition: '0 56%',
		backgroundSize: '100% 10px',
		boxDecorationBreak: 'clone',
		WebkitBoxDecorationBreak: 'clone',
		color: '#78716c',
	};
}

if (typeof module !== 'undefined' && module.exports) {
	module.exports = {
		computeSegments: computeSegments,
		rangesOverlap: rangesOverlap,
		overlapsAny: overlapsAny,
		smartSpaceInsert: smartSpaceInsert,
		applyAccepted: applyAccepted,
		parseInlineMarkdown: parseInlineMarkdown,
		runsForRange: runsForRange,
		hashSeed: hashSeed,
		strikeSlope: strikeSlope,
		buildStrikeBackground: buildStrikeBackground,
		strikeStyle: strikeStyle,
	};
}
