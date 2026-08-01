// Lets the toast on an error response through; see shouldSwapErrorResponse in toast-logic.js.
// Only shouldSwap is flipped: the request did fail, so isError stays true and htmx keeps
// reporting it as failed to htmx:responseError and to detail.successful listeners. The cost is
// a console error per rejection, which beats a listener treating a rejection as a success.
document.body.addEventListener('htmx:beforeSwap', function (evt) {
	const xhr = evt.detail.xhr;
	if (xhr && shouldSwapErrorResponse(xhr.status, xhr.getResponseHeader(SWAP_ERROR_HEADER))) {
		evt.detail.shouldSwap = true;
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
