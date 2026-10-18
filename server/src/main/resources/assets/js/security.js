/* eslint-disable no-unused-vars */

function copyRecoveryCodes(button) {
	const list = button.closest('.recovery-codes').querySelector('.recovery-codes__list');
	const codes = [...list.querySelectorAll('.recovery-codes__code')].map(el => el.textContent.trim()).join('\n');
	const copied = () => { button.lastChild.textContent = ' ' + button.dataset.copied; };

	// The Clipboard API only exists in secure contexts; a plain-http server falls back to selecting the list.
	if (navigator.clipboard) {
		navigator.clipboard.writeText(codes).then(copied);
	} else {
		const range = document.createRange();
		range.selectNodeContents(list);
		const selection = window.getSelection();
		selection.removeAllRanges();
		selection.addRange(range);
		if (document.execCommand('copy')) copied();
	}
}
