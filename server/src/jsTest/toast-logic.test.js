const { test } = require('node:test');
const assert = require('node:assert/strict');
const logic = require('../main/resources/assets/js/toast-logic.js');

test('marked error responses are swapped so their toast survives', () => {
	assert.equal(logic.shouldSwapErrorResponse(400, 'true'), true);
	assert.equal(logic.shouldSwapErrorResponse(422, 'true'), true);
	assert.equal(logic.shouldSwapErrorResponse(500, 'true'), true);
});

test('unmarked error responses keep htmx default handling', () => {
	// A bare 400 would swap an empty body over the target; a 404 would swap a whole error page.
	assert.equal(logic.shouldSwapErrorResponse(400, null), false);
	assert.equal(logic.shouldSwapErrorResponse(404, null), false);
	assert.equal(logic.shouldSwapErrorResponse(401, undefined), false);
	assert.equal(logic.shouldSwapErrorResponse(403, 'false'), false);
	assert.equal(logic.shouldSwapErrorResponse(400, ''), false);
});

test('successful responses are left to htmx even if marked', () => {
	assert.equal(logic.shouldSwapErrorResponse(200, 'true'), false);
	assert.equal(logic.shouldSwapErrorResponse(204, 'true'), false);
	assert.equal(logic.shouldSwapErrorResponse(302, 'true'), false);
	assert.equal(logic.shouldSwapErrorResponse(399, 'true'), false);
});

test('the header name matches what the server sends', () => {
	assert.equal(logic.SWAP_ERROR_HEADER, 'X-Hammer-Swap-Error');
});
