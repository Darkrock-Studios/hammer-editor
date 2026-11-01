/**
 * Declarative event wiring, so templates need no inline on* handlers (the CSP forbids them).
 *
 * An element opts in with data-on-<event>="handler(arg, ...)", e.g.
 *   <button data-on-click="closeShareDialog(event)">
 *   <input data-on-input="onBioInput(value)">
 *
 * `handler` is a global function or one of ACTIONS. Arguments are `this` (the element), `event`,
 * `value` (element.value), `checked` (element.checked) or a quoted string. A handler returning
 * false cancels the default action, as `return false` did in an inline attribute. Listeners go
 * on the element itself, so event.currentTarget and stopPropagation behave as they did inline.
 * Content swapped in by HTMX is wired as it loads.
 */
(function () {
	const EVENTS = ['click', 'input', 'change', 'submit'];
	const CALL = /^\s*([A-Za-z_$][\w$]*)\s*\(([^)]*)\)\s*$/;

	const ACTIONS = {
		preventDefault: function (event) {
			event.preventDefault();
		},
		stopPropagation: function (event) {
			event.stopPropagation();
		},
		selectText: function (el) {
			el.select();
		}
	};

	function resolveArg(token, el, event) {
		token = token.trim();
		switch (token) {
			case 'this':
				return el;
			case 'event':
				return event;
			case 'value':
				return el.value;
			case 'checked':
				return el.checked;
		}
		const quoted = token.match(/^'(.*)'$/);
		if (quoted) return quoted[1];
		throw new Error('Unsupported argument "' + token + '"');
	}

	function wire(el, type) {
		const spec = el.getAttribute('data-on-' + type);
		const call = CALL.exec(spec);
		if (!call) {
			console.error('Bad data-on-' + type + ' on', el, spec);
			return;
		}
		const name = call[1];
		const argTokens = call[2].trim() === '' ? [] : call[2].split(',');

		el.addEventListener(type, function (event) {
			const fn = ACTIONS[name] || window[name];
			if (typeof fn !== 'function') {
				console.error('data-on-' + type + ': no function "' + name + '"');
				return;
			}
			const result = fn.apply(el, argTokens.map(function (t) {
				return resolveArg(t, el, event);
			}));
			if (result === false) event.preventDefault();
		});
	}

	const wired = new WeakMap();

	function wireTree(root) {
		EVENTS.forEach(function (type) {
			const selector = '[data-on-' + type + ']';
			const els = Array.from(root.querySelectorAll(selector));
			if (root.matches && root.matches(selector)) els.push(root);
			els.forEach(function (el) {
				const types = wired.get(el) || new Set();
				if (types.has(type)) return;
				types.add(type);
				wired.set(el, types);
				wire(el, type);
			});
		});
	}

	wireTree(document);
	document.addEventListener('htmx:load', function (event) {
		wireTree(event.target);
	});
})();
