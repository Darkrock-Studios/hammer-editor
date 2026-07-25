// Delegated event wiring, replacing inline on* attributes (which no CSP can allow without
// 'unsafe-inline'). Templates declare `data-on-click="actionName"` and page scripts register the
// handler with `hammerActions({actionName: fn})`.
//
// Listeners live on document, so markup swapped in by HTMX is wired up automatically — there is
// nothing to re-bind after a swap.
(function () {
	const registry = {};

	// click/change/input/submit all bubble to document.
	const EVENTS = ['click', 'change', 'input', 'submit'];

	/** Registers handlers, called as fn(element, event). Returning false prevents the default. */
	window.hammerActions = function (handlers) {
		Object.assign(registry, handlers);
	};

	EVENTS.forEach(function (type) {
		const attribute = 'data-on-' + type;
		document.addEventListener(type, function (event) {
			if (!event.target || !event.target.closest) return;

			const element = event.target.closest('[' + attribute + ']');
			if (!element) return;

			const handler = registry[element.getAttribute(attribute)];
			if (!handler) return;

			if (handler(element, event) === false) {
				event.preventDefault();
			}
		});
	});

	// [data-clears="<selector>"] empties that element before its own htmx request goes out —
	// the declarative stand-in for hx-on::before-request, which htmx compiles with new Function().
	document.addEventListener('htmx:beforeRequest', function (event) {
		const selector = event.target.getAttribute && event.target.getAttribute('data-clears');
		if (!selector) return;

		const target = document.querySelector(selector);
		if (target) target.innerHTML = '';
	});
})();
