// Promotes stylesheets parked at media="print" to the real thing once they have loaded, so they
// never block first render. Drops the fa-pending class once they are all up, releasing the
// placeholder boxes that hold icon width until Font Awesome can paint into them.
(function () {
	var links = document.querySelectorAll('link[data-async-css]');
	var pending = links.length;

	function settle() {
		if (--pending === 0) {
			document.documentElement.classList.remove('fa-pending');
		}
	}

	links.forEach(function (link) {
		if (link.sheet) {
			link.media = 'all';
			settle();
		} else {
			link.addEventListener('load', function () {
				link.media = 'all';
				settle();
			}, {once: true});
			link.addEventListener('error', settle, {once: true});
		}
	});

	if (pending === 0) {
		document.documentElement.classList.remove('fa-pending');
	}
})();
