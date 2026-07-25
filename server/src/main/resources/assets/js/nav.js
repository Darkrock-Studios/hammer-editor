// Mobile hamburger menu in the site header.
(function () {
	const toggle = document.querySelector('.nav-toggle');
	const nav = document.getElementById('header-nav');
	const overlay = document.getElementById('nav-overlay');

	if (!toggle || !nav || !overlay) return;

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
		if (toggle.getAttribute('aria-expanded') === 'true') {
			closeMenu();
		} else {
			openMenu();
		}
	});

	overlay.addEventListener('click', closeMenu);

	nav.querySelectorAll('.header-nav__link').forEach(function (link) {
		link.addEventListener('click', closeMenu);
	});

	document.addEventListener('keydown', function (e) {
		if (e.key === 'Escape') closeMenu();
	});
})();
