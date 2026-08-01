// htmx throws away the body of a 4xx/5xx response, which would take the toast with it. Responses
// built by the server's toast helpers say they are swap payloads; swap those and leave the rest
// to htmx's default handling.
document.body.addEventListener('htmx:beforeSwap', function (evt) {
	const xhr = evt.detail.xhr;
	if (xhr && xhr.status >= 400 && xhr.getResponseHeader('X-Hammer-Swap-Error') === 'true') {
		evt.detail.shouldSwap = true;
		evt.detail.isError = false;
	}
});

// Toast auto-dismiss and manual dismiss handling
function initToast(toast) {
	const duration = 5000;

	// Auto-dismiss after duration
	const timeoutId = setTimeout(() => {
		dismissToast(toast);
	}, duration);

	// Store timeout so we can cancel if manually dismissed
	toast.dataset.timeoutId = timeoutId;

	// Dismiss button handler
	const dismissBtn = toast.querySelector('.toast-dismiss');
	if (dismissBtn) {
		dismissBtn.addEventListener('click', () => {
			clearTimeout(timeoutId);
			dismissToast(toast);
		});
	}
}

function dismissToast(toast) {
	toast.classList.add('toast-dismissing');
	toast.addEventListener('animationend', () => {
		toast.remove();
	}, {once: true});
}

// Watch for new toasts added to container
const toastContainer = document.getElementById('toast-container');
if (toastContainer) {
	const observer = new MutationObserver((mutations) => {
		mutations.forEach((mutation) => {
			mutation.addedNodes.forEach((node) => {
				if (node.nodeType === 1 && node.classList.contains('toast')) {
					initToast(node);
				}
			});
		});
	});
	observer.observe(toastContainer, {childList: true});
}
