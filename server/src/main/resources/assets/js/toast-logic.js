/**
 * Pure decision logic for htmx error responses — no DOM, no globals beyond these
 * functions and constants. Kept DOM-free so it can be unit-tested under Node
 * while still loading as a plain browser <script> (see the dual export at the
 * end), mirroring pen-name-logic.js.
 */

// Set by the server's toast helpers on any error response they build. Must match
// SWAP_ERROR_HEADER in ToastUtils.kt.
const SWAP_ERROR_HEADER = 'X-Hammer-Swap-Error';

/**
 * Whether htmx should swap a response it would otherwise discard. htmx drops the body of a
 * 4xx/5xx by default, which takes the out-of-band toast with it. Only responses the server
 * built as swap payloads opt back in: everything else (a bare error status, a rendered error
 * page from StatusPages) would land in the request's target and wreck it.
 * @param {number} status HTTP status of the response
 * @param {string|null} swapErrorHeader value of the SWAP_ERROR_HEADER response header, if any
 * @returns {boolean}
 */
function shouldSwapErrorResponse(status, swapErrorHeader) {
	return status >= 400 && swapErrorHeader === 'true';
}

if (typeof module !== 'undefined' && module.exports) {
	module.exports = {
		SWAP_ERROR_HEADER: SWAP_ERROR_HEADER,
		shouldSwapErrorResponse: shouldSwapErrorResponse,
	};
}
