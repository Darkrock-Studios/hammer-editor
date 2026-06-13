/**
 * Reviewer markup editor.
 *
 * Renders the manuscript from the JSON island the server emits (#review-data),
 * lets the reviewer select text to suggest delete/reword/comment or click to
 * insert, and autosaves each suggestion to the server. Pure suggestion logic
 * (segments, overlap, smart-spacing, pen-ink) lives in review-logic.js.
 *
 * Functions from review-logic.js are globals here: computeSegments, overlapsAny,
 * smartSpaceInsert, strikeStyle, parseInlineMarkdown, runsForRange.
 */
/* global computeSegments, overlapsAny, smartSpaceInsert, strikeStyle, parseInlineMarkdown, runsForRange */
(function () {
	'use strict';

	const dataEl = document.getElementById('review-data');
	if (!dataEl) return;
	const DATA = JSON.parse(dataEl.textContent);
	const S = JSON.parse(document.getElementById('review-strings').textContent);
	const TOKEN = DATA.token;
	const LOCKED = DATA.locked;

	const TYPE_COLOR = { delete: '#b91c1c', reword: '#1d4ed8', insert: '#15803d', comment: '#a16207' };
	const TYPE_ICON = { delete: 'fa-strikethrough', reword: 'fa-pen-nib', insert: 'fa-plus', comment: 'fa-comment' };
	const TYPE_LABEL = { delete: S.delete, reword: S.reword, insert: S.insert, comment: S.comment };
	const CAVEAT = "'Caveat', cursive";

	let activeScene = 0;
	let activeSugg = null;
	let popup = null; // { para, start, end, text, mode, top, left, draft, reason }

	const root = document.getElementById('review-editor');
	let manuscriptEl = null;

	function scene() { return DATA.scenes[activeScene]; }
	function suggsForScene() { return scene().suggestions; }
	function suggsForPara(p) { return suggsForScene().filter((s) => s.paragraph === p); }

	/* ---------- small DOM helper ---------- */
	function el(tag, props) {
		const node = document.createElement(tag);
		if (props) {
			for (const k in props) {
				if (k === 'style') Object.assign(node.style, props[k]);
				else if (k === 'class') node.className = props[k];
				else if (k.slice(0, 2) === 'on') node.addEventListener(k.slice(2).toLowerCase(), props[k]);
				else if (k === 'data') { for (const d in props.data) node.setAttribute('data-' + d, props.data[d]); }
				else node.setAttribute(k, props[k]);
			}
		}
		for (let i = 2; i < arguments.length; i++) {
			const c = arguments[i];
			if (c == null) continue;
			node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
		}
		return node;
	}
	function icon(name) { return el('i', { class: 'fa-solid ' + name }); }

	/* ---------- offset mapping ---------- */
	// The display omits markdown markers, so DOM positions are NOT source
	// positions. Every rendered manuscript text node is registered here with
	// the source offset its first character corresponds to; selection mapping
	// is then a lookup + local offset. Inserted-ink text is never registered.
	const nodeSrcStart = new WeakMap();

	/** Source offset for (textNode, offsetInNode), or null if not manuscript text. */
	function srcOffsetOf(node, off) {
		if (!nodeSrcStart.has(node)) return null;
		return nodeSrcStart.get(node) + off;
	}

	/** Make a text node carrying styled-run text, registered at its source offset. */
	function runNode(run) {
		const tn = document.createTextNode(run.text);
		nodeSrcStart.set(tn, run.srcStart);
		if (!run.bold && !run.italic) return tn;
		const span = el('span', {
			style: {
				fontWeight: run.bold ? '700' : '',
				fontStyle: run.italic ? 'italic' : '',
			},
		});
		span.appendChild(tn);
		return span;
	}

	/** Append the styled display pieces for source range [start,end) of a paragraph. */
	function appendRange(parent, paraRuns, start, end) {
		runsForRange(paraRuns, start, end).forEach((run) => parent.appendChild(runNode(run)));
	}

	/* ---------- render ---------- */
	function render() {
		root.innerHTML = '';
		const layout = el('div', { class: 'review-editor__layout' });
		layout.appendChild(buildNav());
		layout.appendChild(buildManuscript());
		layout.appendChild(buildGutter());
		root.appendChild(layout);
		manuscriptEl = layout.querySelector('.review-manuscript');
	}

	function buildNav() {
		const nav = el('div', { class: 'review-nav' });
		nav.appendChild(el('h3', { class: 'review-nav__title' }, S.scenesTitle));
		const list = el('div', { class: 'review-nav__list' });
		DATA.scenes.forEach((sc, i) => {
			list.appendChild(el('button', {
				class: 'review-nav__item' + (i === activeScene ? ' review-nav__item--active' : ''),
				type: 'button',
				onclick: () => { activeScene = i; activeSugg = null; popup = null; render(); },
			},
				el('span', { class: 'review-nav__name' }, sc.name),
				el('span', { class: 'review-nav__count' }, String(sc.suggestions.length))
			));
		});
		nav.appendChild(list);
		return nav;
	}

	function buildManuscript() {
		const card = el('div', { class: 'review-manuscript' });
		if (!LOCKED) card.addEventListener('mouseup', onTextMouseUp);
		card.appendChild(el('div', { class: 'review-manuscript__pos' },
			DATA.scenes.length > 1 ? (activeScene + 1) + ' / ' + DATA.scenes.length : ''));
		card.appendChild(el('h2', { class: 'review-manuscript__title' }, scene().name));
		scene().paragraphs.forEach((para) => card.appendChild(buildParagraph(para)));
		return card;
	}

	function buildParagraph(para) {
		const p = el('p', { class: 'review-para', data: { para: para.index } });
		const paraRuns = parseInlineMarkdown(para.text);
		let cur = 0;
		const sorted = suggsForPara(para.index).slice().sort((a, b) => a.start - b.start || a.end - b.end);
		sorted.forEach((s) => {
			if (s.start > cur) appendRange(p, paraRuns, cur, s.start);
			p.appendChild(renderSuggestion(s, paraRuns));
			cur = Math.max(cur, s.end);
		});
		if (cur < para.text.length) appendRange(p, paraRuns, cur, para.text.length);
		return p;
	}

	function renderSuggestion(s, paraRuns) {
		const color = TYPE_COLOR[s.type];
		const active = activeSugg === s.id;
		const onClick = (ev) => { ev.stopPropagation(); activeSugg = active ? null : s.id; render(); };
		const ring = active ? { boxShadow: '0 0 0 2.5px ' + hexToRing(color), borderRadius: '3px' } : {};

		if (s.type === 'delete') {
			const span = el('span', { class: 'review-ink', style: Object.assign({ cursor: 'pointer' }, ring, strikeStyle(color, s.id)), onclick: onClick });
			appendRange(span, paraRuns, s.start, s.end);
			return span;
		}
		if (s.type === 'reword') {
			const struck = el('span', { style: strikeStyle(color, s.id) });
			appendRange(struck, paraRuns, s.start, s.end);
			return el('span', { class: 'review-ink', style: Object.assign({ cursor: 'pointer' }, ring), onclick: onClick },
				struck,
				el('span', { 'data-ins': '1', style: { fontFamily: CAVEAT, fontSize: '1.15em', fontWeight: '600', color: color } }, ' ' + (s.replacement || ''))
			);
		}
		if (s.type === 'insert') {
			return el('span', { class: 'review-ink', style: Object.assign({ cursor: 'pointer' }, ring), onclick: onClick },
				el('span', { 'data-ins': '1', style: { color: color, fontWeight: '700' } }, '‸'),
				el('span', { 'data-ins': '1', style: { fontFamily: CAVEAT, fontSize: '1.15em', fontWeight: '600', color: color } }, s.replacement || '')
			);
		}
		// comment: yellow marker swipe
		const span = el('span', {
			class: 'review-ink', onclick: onClick,
			style: Object.assign({ cursor: 'pointer', background: 'rgba(254,240,138,0.85)', borderRadius: '2px' }, ring),
		});
		appendRange(span, paraRuns, s.start, s.end);
		return span;
	}

	function buildGutter() {
		const gutter = el('div', { class: 'review-gutter' });
		gutter.appendChild(el('h3', { class: 'review-gutter__title' }, S.suggestionsTitle));
		const cards = el('div', { class: 'review-gutter__cards' });
		const list = suggsForScene().slice().sort((a, b) => a.paragraph - b.paragraph || a.start - b.start);
		if (list.length === 0) cards.appendChild(el('div', { class: 'review-card--empty' }, S.noSuggestions));
		list.forEach((s) => cards.appendChild(buildCard(s)));
		gutter.appendChild(cards);
		return gutter;
	}

	function buildCard(s) {
		const color = TYPE_COLOR[s.type];
		const active = activeSugg === s.id;
		const card = el('div', {
			class: 'review-card' + (active ? ' review-card--active' : ''),
			style: { borderColor: active ? color : '' },
			onclick: () => { activeSugg = active ? null : s.id; render(); },
		});
		const head = el('div', { class: 'review-card__head' },
			el('span', { class: 'review-card__type', style: { color: color } }, icon(TYPE_ICON[s.type]), ' ' + TYPE_LABEL[s.type]));
		if (!LOCKED) {
			head.appendChild(el('button', {
				class: 'review-card__remove', title: S.remove, type: 'button',
				onclick: (ev) => { ev.stopPropagation(); doRemove(s); },
			}, icon('fa-trash-can')));
		}
		card.appendChild(head);
		if (s.type !== 'insert' && s.original) card.appendChild(el('div', { class: 'review-card__quote' }, '“' + s.original + '”'));
		if ((s.type === 'reword' || s.type === 'insert') && s.replacement) {
			card.appendChild(el('div', { class: 'review-card__replacement', style: { color: color } },
				icon('fa-arrow-right'), ' ' + s.replacement.trim()));
		}
		if (s.reason) card.appendChild(el('div', { class: 'review-card__reason' }, s.reason));
		return card;
	}

	/* ---------- selection / popup ---------- */
	function onTextMouseUp(e) {
		if (LOCKED) return;
		if (e.target.closest && e.target.closest('[data-popup]')) return;
		const onInk = e.target.closest && e.target.closest('[data-ins]');
		setTimeout(() => {
			const sel = window.getSelection();
			if (!sel || sel.rangeCount === 0 || sel.isCollapsed) { placeCaret(sel, onInk); return; }
			const range = sel.getRangeAt(0);
			const sc = range.startContainer, ec = range.endContainer;
			if (sc.nodeType !== 3 || ec.nodeType !== 3) return;
			const sp = sc.parentElement.closest('[data-para]');
			const ep = ec.parentElement.closest('[data-para]');
			if (!sp || sp !== ep) return;
			const para = parseInt(sp.getAttribute('data-para'), 10);
			const start = srcOffsetOf(sc, range.startOffset);
			const end = srcOffsetOf(ec, range.endOffset);
			if (start == null || end == null || end <= start) return;
			if (overlapsAny(start, end, suggsForPara(para))) return;
			openPopup({ para: para, start: start, end: end, text: paraTextOf(para).slice(start, end), mode: 'menu' }, range.getBoundingClientRect());
		}, 10);
	}

	function placeCaret(sel, onInk) {
		if (onInk) return;
		const r = sel && sel.rangeCount ? sel.getRangeAt(0) : null;
		const n = r ? r.startContainer : null;
		if (!n || n.nodeType !== 3) { closePopup(); return; }
		const pe = n.parentElement.closest('[data-para]');
		if (!pe) { closePopup(); return; }
		const para = parseInt(pe.getAttribute('data-para'), 10);
		const pos = srcOffsetOf(n, r.startOffset);
		if (pos == null) { closePopup(); return; }
		for (const s of suggsForPara(para)) {
			if (s.start === s.end) { if (pos === s.start) return; continue; }
			if (pos > s.start && pos < s.end) { closePopup(); return; }
		}
		openPopup({ para: para, start: pos, end: pos, text: '', mode: 'caret' }, r.getBoundingClientRect());
	}

	function openPopup(p, rect) {
		const crect = manuscriptEl.getBoundingClientRect();
		p.top = rect.top - crect.top - 10;
		p.left = Math.max(120, Math.min(rect.left + rect.width / 2 - crect.left, crect.width - 120));
		p.draft = '';
		p.reason = '';
		popup = p;
		renderPopup();
	}
	function closePopup() { if (popup) { popup = null; renderPopup(); } }

	function renderPopup() {
		const existing = manuscriptEl.querySelector('[data-popup]');
		if (existing) existing.remove();
		if (popup) manuscriptEl.appendChild(buildPopup());
	}

	function buildPopup() {
		const pos = { position: 'absolute', top: popup.top + 'px', left: popup.left + 'px', transform: 'translate(-50%,-100%)', zIndex: '60' };
		if (popup.mode === 'caret') {
			return el('div', { 'data-popup': '1', class: 'review-popup review-popup--menu', style: pos },
				menuBtn(S.insertHere, 'fa-plus', () => { popup.mode = 'insert'; renderPopup(); }));
		}
		if (popup.mode === 'menu') {
			return el('div', { 'data-popup': '1', class: 'review-popup review-popup--menu', style: pos },
				menuBtn(S.reword, 'fa-pen-nib', () => { popup.mode = 'reword'; renderPopup(); }),
				menuBtn(S.delete, 'fa-strikethrough', () => commit('delete', '', '')),
				menuBtn(S.insert, 'fa-plus', () => { popup.mode = 'insert'; renderPopup(); }),
				menuBtn(S.comment, 'fa-comment', () => { popup.mode = 'comment'; renderPopup(); })
			);
		}
		const title = popup.mode === 'reword' ? S.replacementLabel : popup.mode === 'insert' ? S.insertLabel : S.commentLabel;
		const saveBtn = el('button', {
			class: 'btn btn--accent review-popup__save', type: 'button', disabled: 'true',
			onclick: () => {
				if (!popup.draft.trim()) return;
				if (popup.mode === 'comment') commit('comment', '', popup.draft.trim());
				else commit(popup.mode, popup.draft, (popup.reason || '').trim());
			},
		}, S.save);
		const textarea = el('textarea', {
			class: 'review-popup__textarea',
			placeholder: popup.mode === 'comment' ? S.commentPlaceholder : S.typePlaceholder,
			oninput: (e) => { popup.draft = e.target.value; saveBtn.disabled = !popup.draft.trim(); },
		});
		const wrap = el('div', { 'data-popup': '1', class: 'review-popup review-popup--form', style: pos },
			el('div', { class: 'review-popup__label' }, title), textarea);
		if (popup.mode !== 'comment') {
			wrap.appendChild(el('input', {
				class: 'review-popup__reason', placeholder: S.reasonPlaceholder,
				oninput: (e) => { popup.reason = e.target.value; },
			}));
		}
		wrap.appendChild(el('div', { class: 'review-popup__actions' },
			el('button', { class: 'btn btn--ghost', type: 'button', onclick: closePopup }, S.cancel), saveBtn));
		setTimeout(() => textarea.focus(), 0);
		return wrap;
	}

	function menuBtn(label, ic, fn) {
		return el('button', { class: 'review-popup__btn', type: 'button', onclick: fn }, icon(ic), ' ' + label);
	}

	/* ---------- persistence ---------- */
	async function commit(type, replacement, reason) {
		const p = popup;
		if (!p) return;
		const isInsert = type === 'insert';
		let repl = replacement || '';
		if (isInsert && repl) repl = smartSpaceInsert(paraTextOf(p.para), p.end, repl);
		const payload = {
			reviewSceneId: scene().reviewSceneId, type: type, paragraph: p.para,
			start: isInsert ? p.end : p.start, end: p.end, replacement: repl, reason: reason || '',
		};
		closePopup();
		clearSelection();
		const saved = await postSuggestion(payload);
		if (saved) {
			scene().suggestions.push(toClient(saved));
			activeSugg = saved.id;
			render();
		}
	}

	async function doRemove(s) {
		if (!window.confirm(S.removeConfirm)) return;
		if (await deleteSuggestion(s.id)) {
			scene().suggestions = scene().suggestions.filter((x) => x.id !== s.id);
			if (activeSugg === s.id) activeSugg = null;
			render();
		}
	}

	async function postSuggestion(payload) {
		const body = new URLSearchParams();
		Object.keys(payload).forEach((k) => { if (payload[k] != null) body.set(k, payload[k]); });
		try {
			const res = await fetch('/review/' + TOKEN + '/suggestions', {
				method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body,
			});
			if (!res.ok) { toastError(); return null; }
			return await res.json();
		} catch (e) { toastError(); return null; }
	}

	async function deleteSuggestion(id) {
		try {
			const res = await fetch('/review/' + TOKEN + '/suggestions/' + id, { method: 'DELETE' });
			if (!res.ok && res.status !== 404) { toastError(); return false; }
			return true;
		} catch (e) { toastError(); return false; }
	}

	// Server wire shape -> client suggestion shape (adds `original` for the gutter quote).
	function toClient(saved) {
		return {
			id: saved.id, reviewSceneId: saved.reviewSceneId, type: saved.type,
			paragraph: saved.paragraph, start: saved.start, end: saved.end,
			replacement: saved.replacement || '', reason: saved.reason || '',
			original: saved.type === 'insert' ? '' : displayText(saved.paragraph, saved.start, saved.end),
		};
	}

	/* ---------- submit ---------- */
	function initSubmit() {
		const btn = document.getElementById('review-submit-btn');
		if (!btn) return;
		btn.addEventListener('click', async () => {
			if (!window.confirm(S.submitConfirm)) return;
			btn.disabled = true;
			try {
				const res = await fetch('/review/' + TOKEN + '/submit', { method: 'POST' });
				if (res.ok) { window.location.reload(); return; }
			} catch (e) { /* fallthrough */ }
			btn.disabled = false;
			toastError();
		});
	}

	/* ---------- helpers ---------- */
	function paraTextOf(index) {
		const para = scene().paragraphs.find((p) => p.index === index);
		return para ? para.text : '';
	}
	/** Display text for a source slice: emphasis markers dropped, like the manuscript shows it. */
	function displayText(paraIndex, start, end) {
		const runs = parseInlineMarkdown(paraTextOf(paraIndex));
		return runsForRange(runs, start, end).map((r) => r.text).join('');
	}
	function clearSelection() { const sel = window.getSelection(); if (sel) sel.removeAllRanges(); }
	function hexToRing(hex) {
		const n = parseInt(hex.slice(1), 16);
		return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',0.35)';
	}
	function toastError() {
		const container = document.getElementById('toast-container');
		if (!container) return;
		const t = el('div', { class: 'toast toast-error', role: 'alert' },
			el('span', { class: 'toast-message' }, S.saveFailed),
			el('button', { class: 'toast-dismiss', onclick: function () { t.remove(); } }, '×'));
		container.appendChild(t);
		setTimeout(() => t.remove(), 5000);
	}

	document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closePopup(); });

	// Give each suggestion an `original` display slice (markers dropped) for the gutter quote.
	DATA.scenes.forEach((sc) => {
		sc.suggestions = sc.suggestions.map((s) => Object.assign({}, s, {
			original: s.type === 'insert' ? '' : (function () {
				const para = sc.paragraphs.find((p) => p.index === s.paragraph);
				if (!para) return '';
				return runsForRange(parseInlineMarkdown(para.text), s.start, s.end)
					.map((r) => r.text).join('');
			})(),
		}));
	});

	render();
	initSubmit();
})();
