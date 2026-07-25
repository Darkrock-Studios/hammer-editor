// Admin users page: collapsible sort controls, with the collapsed state remembered per browser.
/* global hammerActions */
(function () {
	function toggleSortControls() {
		const controls = document.getElementById('sortControls');
		const content = document.getElementById('sortControlsContent');
		const icon = document.getElementById('sortControlsIcon');

		if (controls && content && icon) {
			controls.classList.toggle('sort-controls--collapsed');
			const isCollapsed = controls.classList.contains('sort-controls--collapsed');
			localStorage.setItem('sortControlsCollapsed', isCollapsed);
		}
	}

	function restoreSortControlsState() {
		const controls = document.getElementById('sortControls');
		if (!controls) return;

		// Collapsed unless the user has explicitly expanded it.
		if (localStorage.getItem('sortControlsCollapsed') === 'false') {
			controls.classList.remove('sort-controls--collapsed');
		} else {
			controls.classList.add('sort-controls--collapsed');
		}
	}

	hammerActions({'sort-controls-toggle': toggleSortControls});

	document.addEventListener('htmx:afterSwap', function (event) {
		if (event.detail.target.id === 'users') {
			restoreSortControlsState();
		}
	});

	restoreSortControlsState();
})();
