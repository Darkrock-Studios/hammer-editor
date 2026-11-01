(function () {
	const toggle = document.querySelector('.nav-toggle');
	const nav = document.getElementById('header-nav');
	const overlay = document.getElementById('nav-overlay');

	function closeMenu() {
		toggle.setAttribute('aria-expanded', 'false');
		nav.classList.remove('is-open');
		overlay.classList.remove('is-visible');
		document.body.style.overflow = '';
	}

	function openMenu() {
		toggle.setAttribute('aria-expanded', 'true');
		nav.classList.add('is-open');
		overlay.classList.add('is-visible');
		document.body.style.overflow = 'hidden';
	}

	toggle.addEventListener('click', function () {
		const isOpen = toggle.getAttribute('aria-expanded') === 'true';
		if (isOpen) {
			closeMenu();
		} else {
			openMenu();
		}
	});

	overlay.addEventListener('click', closeMenu);

	// Close menu when clicking a nav link
	nav.querySelectorAll('.header-nav__link').forEach(function (link) {
		link.addEventListener('click', closeMenu);
	});

	// Close on escape key
	document.addEventListener('keydown', function (e) {
		if (e.key === 'Escape') closeMenu();
	});

	// Community badge: pointer devices get it on hover from CSS, touch needs a tap.
	const badge = document.querySelector('.community-badge');
	if (badge) {
		const item = badge.closest('.header-nav__item');

		function closeBadge() {
			badge.setAttribute('aria-expanded', 'false');
		}

		badge.addEventListener('click', function (e) {
			e.stopPropagation();
			const isOpen = badge.getAttribute('aria-expanded') === 'true';
			badge.setAttribute('aria-expanded', String(!isOpen));
		});

		document.addEventListener('click', function (e) {
			if (!item.contains(e.target)) closeBadge();
		});

		document.addEventListener('keydown', function (e) {
			if (e.key === 'Escape') closeBadge();
		});
	}
})();
