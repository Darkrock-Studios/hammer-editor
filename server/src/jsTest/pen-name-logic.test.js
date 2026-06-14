const { test } = require('node:test');
const assert = require('node:assert/strict');
const logic = require('../main/resources/assets/js/pen-name-logic.js');

test('validateClientSide accepts a name at and within the length bounds', () => {
	assert.deepEqual(logic.validateClientSide('Alice'), { valid: true, message: '' });
	assert.equal(logic.validateClientSide('a'.repeat(logic.PEN_NAME_MIN_LENGTH)).valid, true);
	assert.equal(logic.validateClientSide('a'.repeat(logic.PEN_NAME_MAX_LENGTH)).valid, true);
});

test('validateClientSide treats empty or whitespace-only input as invalid but silent', () => {
	assert.deepEqual(logic.validateClientSide(''), { valid: false, message: '' });
	assert.deepEqual(logic.validateClientSide('   '), { valid: false, message: '' });
});

test('validateClientSide rejects names just below the minimum length', () => {
	const result = logic.validateClientSide('a'.repeat(logic.PEN_NAME_MIN_LENGTH - 1));
	assert.equal(result.valid, false);
	assert.match(result.message, /At least 4 characters/);
});

test('validateClientSide rejects names just above the maximum length', () => {
	const result = logic.validateClientSide('a'.repeat(logic.PEN_NAME_MAX_LENGTH + 1));
	assert.equal(result.valid, false);
	assert.match(result.message, /Maximum 32 characters/);
});

test('validateClientSide measures length after trimming surrounding whitespace', () => {
	// Three real letters plus padding: trimmed length is below the minimum.
	assert.equal(logic.validateClientSide('  abc  ').valid, false);
	// A name that only fits once padding is trimmed is accepted.
	assert.equal(logic.validateClientSide('   Alice   ').valid, true);
});

test('validateClientSide requires the name to start with a letter', () => {
	for (const name of ['1abcd', ' _abcd'.trim(), '-abcd', '9lives']) {
		const result = logic.validateClientSide(name);
		assert.equal(result.valid, false, `expected ${name} to be rejected`);
		assert.match(result.message, /start with a letter/);
	}
});

test('validateClientSide allows letters, numbers, spaces, hyphens and underscores', () => {
	assert.equal(logic.validateClientSide('Jane Doe').valid, true);
	assert.equal(logic.validateClientSide('Jane-Doe_99').valid, true);
});

test('validateClientSide accepts Unicode letters beyond ASCII', () => {
	assert.equal(logic.validateClientSide('Élodie').valid, true);
	assert.equal(logic.validateClientSide('夏目漱石').valid, true);
});

test('validateClientSide rejects disallowed punctuation', () => {
	for (const name of ['Jane.Doe', 'Jane@Doe', 'Jane!', 'Jane/Doe']) {
		const result = logic.validateClientSide(name);
		assert.equal(result.valid, false, `expected ${name} to be rejected`);
		assert.match(result.message, /Only letters, numbers/);
	}
});
