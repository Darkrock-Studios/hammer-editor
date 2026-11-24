const { test } = require('node:test');
const assert = require('node:assert/strict');
const { copyText } = require('../main/resources/assets/js/copy-button-logic.js');

const clipboard = (outcome) => ({
	written: null,
	writeText(text) {
		this.written = text;
		return outcome === 'ok' ? Promise.resolve() : Promise.reject(new Error('denied'));
	},
});

test('the async clipboard is used when available', async () => {
	const cb = clipboard('ok');
	let fellBack = false;

	const ok = await copyText('secret', cb, () => { fellBack = true; return true; });

	assert.equal(ok, true);
	assert.equal(cb.written, 'secret');
	assert.equal(fellBack, false);
});

test('a refused async copy falls back to execCommand', async () => {
	assert.equal(await copyText('secret', clipboard('denied'), () => true), true);
	assert.equal(await copyText('secret', clipboard('denied'), () => false), false);
});

test('without the async clipboard only a true execCommand result counts as copied', async () => {
	assert.equal(await copyText('secret', undefined, () => true), true);
	assert.equal(await copyText('secret', undefined, () => false), false);
	assert.equal(await copyText('secret', null, () => { throw new Error('unsupported'); }), false);
});
