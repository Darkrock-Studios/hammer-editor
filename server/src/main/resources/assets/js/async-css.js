// Promotes stylesheets parked at media="print" to the real thing once they have loaded, so they
// never block first render.
document.querySelectorAll('link[data-async-css]').forEach(function (link) {
	if (link.sheet) {
		link.media = 'all';
	} else {
		link.addEventListener('load', function () {
			link.media = 'all';
		}, {once: true});
	}
});
