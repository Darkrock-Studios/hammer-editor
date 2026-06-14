const { test } = require('node:test');
const assert = require('node:assert/strict');
const logic = require('../main/resources/assets/js/review-form-logic.js');

test('nextToggleState checks all while any box is unchecked', () => {
	assert.equal(logic.nextToggleState([true, false, true]), true);
	assert.equal(logic.nextToggleState([false, false]), true);
});

test('nextToggleState clears once every box is checked', () => {
	assert.equal(logic.nextToggleState([true, true, true]), false);
});

test('nextToggleState clears an empty set', () => {
	assert.equal(logic.nextToggleState([]), false);
});

test('allSelected is true only when non-empty and every box is checked', () => {
	assert.equal(logic.allSelected([true, true]), true);
	assert.equal(logic.allSelected([true, false]), false);
	assert.equal(logic.allSelected([]), false);
});

test('reviewCountLabel shows the none-label when nothing is selected', () => {
	assert.equal(logic.reviewCountLabel(0, 12, 'none selected'), 'none selected');
});

test('reviewCountLabel reports the selected count out of the total', () => {
	assert.equal(logic.reviewCountLabel(3, 12, 'none selected'), '3 of 12 selected');
	assert.equal(logic.reviewCountLabel(1, '1', 'none selected'), '1 of 1 selected');
});

test('canSendReview requires at least one scene and an emailish address', () => {
	assert.equal(logic.canSendReview(2, 'a@b.com'), true);
	assert.equal(logic.canSendReview(0, 'a@b.com'), false); // no scenes
	assert.equal(logic.canSendReview(2, 'not-an-email'), false); // no '@'
	assert.equal(logic.canSendReview(2, ''), false); // empty email
});
