function toggleSortControls() {
	const controls = document.getElementById('sortControls');
	const content = document.getElementById('sortControlsContent');
	const icon = document.getElementById('sortControlsIcon');

	if (controls && content && icon) {
		controls.classList.toggle('sort-controls--collapsed');

		// Store preference in localStorage
		const isCollapsed = controls.classList.contains('sort-controls--collapsed');
		localStorage.setItem('sortControlsCollapsed', isCollapsed);
	}
}

// Restore collapse state on page load
document.addEventListener('htmx:afterSwap', function (event) {
	if (event.detail.target.id === 'users') {
		restoreSortControlsState();
	}
});

function restoreSortControlsState() {
	const controls = document.getElementById('sortControls');
	if (!controls) return;

	const storedValue = localStorage.getItem('sortControlsCollapsed');

	// If user has explicitly expanded it (stored as 'false'), remove collapsed class
	if (storedValue === 'false') {
		controls.classList.remove('sort-controls--collapsed');
	} else {
		// Default to collapsed (either no value or 'true')
		controls.classList.add('sort-controls--collapsed');
	}
}

// Initial restore on page load
document.addEventListener('DOMContentLoaded', function () {
	restoreSortControlsState();
});
