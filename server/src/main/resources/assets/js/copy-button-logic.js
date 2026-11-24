/**
 * Pure clipboard decision logic, DOM-free so it runs under Node tests and as a
 * plain browser <script> (see the dual export at the end).
 */

/**
 * Copies [text], falling back to [execCopy] where the async clipboard is missing
 * (insecure plain-HTTP origins) or refuses. Resolves true only when the text
 * actually reached the clipboard: execCommand reports failure by returning
 * false, not by throwing.
 * @param {string} text
 * @param {{writeText: function(string): Promise<void>}|undefined|null} clipboard
 * @param {function(): boolean} execCopy selection-based copy returning execCommand's result
 * @returns {Promise<boolean>}
 */
function copyText(text, clipboard, execCopy) {
	const fallback = function () {
		try {
			return execCopy() === true;
		} catch (err) {
			return false;
		}
	};
	if (clipboard && typeof clipboard.writeText === 'function') {
		return clipboard.writeText(text).then(function () { return true; }, fallback);
	}
	return Promise.resolve(fallback());
}

if (typeof module !== 'undefined' && module.exports) {
	module.exports = { copyText: copyText };
}
