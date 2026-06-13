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

/** True if [aStart,aEnd) and [bStart,bEnd) share any character (zero-width carets never overlap). */
function rangesOverlap(aStart, aEnd, bStart, bEnd) {
	if (aStart === aEnd || bStart === bEnd) return false;
	return !(aEnd <= bStart || aStart >= bEnd);
}

/** True if a new range overlaps any existing non-caret suggestion in the same paragraph. */
function overlapsAny(start, end, suggestions) {
	return suggestions.some(function (s) {
		return rangesOverlap(start, end, s.start, s.end);
	});
}

/**
 * Pad an inserted string with a leading space only when it reads as a new word
 * after a non-space — but not when inserting punctuation. Mirrors the prototype.
 */
function smartSpaceInsert(paraText, start, replacement) {
	if (!replacement) return replacement;
	const prev = start > 0 ? paraText[start - 1] : '';
	const first = replacement[0];
	const punct = ',.;:!?—’”)…-';
	const needsSpace = !/\s/.test(first) && punct.indexOf(first) === -1 && prev && !/\s/.test(prev);
	return needsSpace ? ' ' + replacement : replacement;
}

/**
 * Produce the final paragraph text after applying a set of accepted suggestions.
 * Applied right-to-left so earlier offsets stay valid. Rejected/undecided
 * suggestions (status !== 'accepted') are left out. Comments never change text.
 */
function applyAccepted(text, suggestions) {
	const accepted = suggestions
		.filter(function (s) { return s.status === 'accepted' && s.type !== 'comment'; })
		.sort(function (a, b) { return b.start - a.start || b.end - a.end; });
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
 * @returns {Array<{text:string, srcStart:number, bold:boolean, italic:boolean}>}
 */
function parseInlineMarkdown(text) {
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
			// closes after non-space; for single _ require not intraword
			if (prev && !/\s/.test(prev) && prev !== marker[0] &&
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

const STRIKE_TILE = 140;

/** A repeating-x SVG background drawing a straight pen line at the seed's slight angle. */
function buildStrikeBackground(color, seed) {
	const dy = strikeSlope(seed);
	const y1 = (5 - dy / 2).toFixed(2);
	const y2 = (5 + dy / 2).toFixed(2);
	const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + STRIKE_TILE +
		'" height="10" viewBox="0 0 ' + STRIKE_TILE + ' 10" preserveAspectRatio="none">' +
		'<line x1="0" y1="' + y1 + '" x2="' + STRIKE_TILE + '" y2="' + y2 +
		'" stroke="' + color + '" stroke-width="2" stroke-linecap="round"/></svg>';
	return 'url("data:image/svg+xml,' + encodeURIComponent(svg) + '")';
}

/** Inline style object that strikes text with the seed's pen line; spreads onto element.style. */
function strikeStyle(color, seed) {
	return {
		backgroundImage: buildStrikeBackground(color, seed),
		backgroundRepeat: 'repeat-x',
		backgroundPosition: '0 56%',
		backgroundSize: STRIKE_TILE + 'px 10px',
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
