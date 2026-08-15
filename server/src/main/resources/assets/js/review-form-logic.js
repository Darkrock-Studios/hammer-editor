/**
 * Pure helpers for the editorial-review request form (scene selection) — no DOM.
 * Loaded as a pre-script before story.js so these are globals there, and
 * dual-exported for Node unit tests, mirroring review-logic.js.
 *
 * Callers pass plain checked flags (boolean[]), not checkbox elements, so the
 * decisions stay DOM-free.
 */

/**
 * The next checked state for a "Select all / Clear" toggle: while any box is
 * unchecked the next action checks them all; once they are all checked it
 * clears them. An empty set clears (nothing to select).
 * @param {boolean[]} checkedStates
 * @returns {boolean} the state every box should be set to
 */
function nextToggleState(checkedStates) {
	return checkedStates.some(function (c) { return !c; });
}

/**
 * Whether a toggle should read "Clear"/"Clear all" rather than "Select all":
 * true only once there is at least one box and every one is checked.
 * @param {boolean[]} checkedStates
 */
function allSelected(checkedStates) {
	return checkedStates.length > 0 && checkedStates.every(function (c) { return c; });
}

/**
 * The selected-scene counter text.
 * @param {number} checked - number of selected scenes
 * @param {string|number} total - total scenes available
 * @param {string} noneLabel - text shown when nothing is selected
 */
function reviewCountLabel(checked, total, noneLabel) {
	return checked === 0 ? noneLabel : checked + ' of ' + total + ' selected';
}

/**
 * Whether the review can be sent: at least one scene picked and a plausible
 * email address (contains '@').
 * @param {number} checkedCount
 * @param {string} email
 */
function canSendReview(checkedCount, email) {
	return checkedCount > 0 && !!email && email.includes('@');
}

/**
 * Whether a private share can be created: either it isn't limited to specific
 * scenes, or at least one scene is picked.
 * @param {boolean} limitEnabled
 * @param {number} checkedCount
 */
function canCreateShare(limitEnabled, checkedCount) {
	return !limitEnabled || checkedCount > 0;
}

if (typeof module !== 'undefined' && module.exports) {
	module.exports = {
		nextToggleState: nextToggleState,
		allSelected: allSelected,
		reviewCountLabel: reviewCountLabel,
		canSendReview: canSendReview,
		canCreateShare: canCreateShare,
	};
}
