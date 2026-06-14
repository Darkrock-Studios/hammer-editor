/**
 * Pure pen-name validation for the dashboard — no DOM, no globals beyond these
 * functions and constants. Kept DOM-free so it can be unit-tested under Node
 * while still loading as a plain browser <script> (see the dual export at the
 * end), mirroring review-logic.js.
 */

const PEN_NAME_MIN_LENGTH = 4;
const PEN_NAME_MAX_LENGTH = 32;
// Unicode letters, numbers, spaces, hyphens, underscores — must start with a letter.
const PEN_NAME_PATTERN = /^[\p{L}][\p{L}\p{N} _-]*$/u;

/**
 * Validate a pen name against the client-side rules. Whitespace is trimmed
 * before checking. An empty (or whitespace-only) value is invalid with no
 * message, so the UI can stay quiet until the user has typed something.
 * @returns {{valid: boolean, message: string}}
 */
function validateClientSide(penName) {
	const trimmed = penName.trim();

	if (trimmed.length === 0) {
		return { valid: false, message: '' };
	}
	if (trimmed.length < PEN_NAME_MIN_LENGTH) {
		return { valid: false, message: `At least ${PEN_NAME_MIN_LENGTH} characters needed` };
	}
	if (trimmed.length > PEN_NAME_MAX_LENGTH) {
		return { valid: false, message: `Maximum ${PEN_NAME_MAX_LENGTH} characters allowed` };
	}
	if (!PEN_NAME_PATTERN.test(trimmed)) {
		return {
			valid: false,
			message: 'Must start with a letter. Only letters, numbers, spaces, hyphens, and underscores allowed.'
		};
	}
	return { valid: true, message: '' };
}

if (typeof module !== 'undefined' && module.exports) {
	module.exports = {
		PEN_NAME_MIN_LENGTH: PEN_NAME_MIN_LENGTH,
		PEN_NAME_MAX_LENGTH: PEN_NAME_MAX_LENGTH,
		PEN_NAME_PATTERN: PEN_NAME_PATTERN,
		validateClientSide: validateClientSide,
	};
}
