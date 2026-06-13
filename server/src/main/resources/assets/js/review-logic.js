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
		hashSeed: hashSeed,
		strikeSlope: strikeSlope,
		buildStrikeBackground: buildStrikeBackground,
		strikeStyle: strikeStyle,
	};
}
