/**
 * Review markup editor, in two modes driven by the #review-data island:
 *  - reviewer: select text to suggest delete/reword/comment or click to insert;
 *    each suggestion autosaves against the capability token.
 *  - author: read the submitted suggestions, accept/reject each one (the
 *    manuscript previews the result), then apply the revisions.
 * Pure suggestion logic (segments, overlap, smart-spacing, pen-ink) lives in
 * review-logic.js.
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
	const IS_AUTHOR = DATA.mode === 'author';
	const CAN_DECIDE = IS_AUTHOR && DATA.canDecide;
	const BASE = DATA.basePath || '';

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
		caretMarker = null; // discarded with the rebuilt DOM
		tip = null;
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
				sc.done ? el('span', { class: 'review-nav__done', title: S.doneMarked }, icon('fa-circle-check')) : null,
				el('span', { class: 'review-nav__count' }, String(sc.suggestions.length))
			));
		});
		nav.appendChild(list);
		return nav;
	}

	// Resolve a selection endpoint to a concrete text position. Real drag
	// selections often land on element nodes at run boundaries; descend to the
	// nearest text node so those selections still produce a popup.
	function resolvePoint(container, offset) {
		if (container.nodeType === 3) return { node: container, off: offset };
		if (container.nodeType !== 1) return null;
		const before = container.childNodes[offset];
		const probe = before || container.lastChild;
		if (!probe) return null;
		// boundary after the previous node when we're past the last child
		const findText = (node, last) => {
			if (!node) return null;
			if (node.nodeType === 3) return node;
			const kids = node.childNodes;
			for (let i = 0; i < kids.length; i++) {
				const k = kids[last ? kids.length - 1 - i : i];
				const found = findText(k, last);
				if (found) return found;
			}
			return null;
		};
		if (before) {
			const tn = findText(before, false);
			return tn ? { node: tn, off: 0 } : null;
		}
		const tn = findText(probe, true);
		return tn ? { node: tn, off: tn.nodeValue.length } : null;
	}

	function buildManuscript() {
		const card = el('div', { class: 'review-manuscript' });
		card.appendChild(el('div', { class: 'review-manuscript__pos' },
			DATA.scenes.length > 1 ? (activeScene + 1) + ' / ' + DATA.scenes.length : ''));
		card.appendChild(el('h2', { class: 'review-manuscript__title' }, scene().name));
		scene().paragraphs.forEach((para) => card.appendChild(buildParagraph(para)));
		if (!IS_AUTHOR && !LOCKED) card.appendChild(buildDoneToggle());
		return card;
	}

	/**
	 * "Mark scene as done" at the end of the manuscript: a progress signal the
	 * author can watch. Marking the last unread scene done stays put; otherwise
	 * it advances to the next not-done scene, reading-flow style.
	 */
	function buildDoneToggle() {
		const sc = scene();
		return el('div', { class: 'review-done' },
			el('button', {
				class: 'review-done-btn' + (sc.done ? ' review-done-btn--done' : ''),
				type: 'button',
				onclick: async () => {
					const next = !sc.done;
					try {
						const res = await fetch('/review/' + TOKEN + '/scenes/' + sc.reviewSceneId + '/done', {
							method: 'POST',
							headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
							body: new URLSearchParams({ done: String(next) }),
						});
						if (!res.ok) { toastError(); return; }
					} catch (e) { toastError(); return; }
					sc.done = next;
					if (next) {
						for (let k = 1; k < DATA.scenes.length; k++) {
							const j = (activeScene + k) % DATA.scenes.length;
							if (!DATA.scenes[j].done) {
								activeScene = j;
								window.scrollTo({ top: 0, behavior: 'smooth' });
								break;
							}
						}
					}
					activeSugg = null;
					popup = null;
					render();
				},
			}, icon('fa-circle-check'), ' ' + (sc.done ? S.doneMarked : S.doneMark)));
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

	/** Cross-highlight a suggestion's ink and gutter card while hovering either one. */
	function linkHover(id, on) {
		const ink = manuscriptEl && manuscriptEl.querySelector('[data-sugg-id="' + id + '"]');
		const card = root.querySelector('[data-card-id="' + id + '"]');
		if (ink) ink.classList.toggle('review-ink--linked', on);
		if (card) card.classList.toggle('review-card--linked', on);
	}

	/**
	 * Select a suggestion and scroll its counterpart into view: tapping ink
	 * brings the gutter card over, tapping the card brings the ink over.
	 */
	function selectSuggestion(s, source) {
		activeSugg = activeSugg === s.id ? null : s.id;
		popup = null;
		render();
		if (activeSugg !== s.id) return;
		const target = source === 'ink'
			? root.querySelector('[data-card-id="' + s.id + '"]')
			: manuscriptEl.querySelector('[data-sugg-id="' + s.id + '"]');
		if (target) target.scrollIntoView({ block: source === 'ink' ? 'nearest' : 'center', behavior: 'smooth' });
	}

	function renderSuggestion(s, paraRuns) {
		const color = TYPE_COLOR[s.type];
		const active = activeSugg === s.id;
		const onClick = (ev) => { ev.stopPropagation(); selectSuggestion(s, 'ink'); };
		const ring = active ? { boxShadow: '0 0 0 2.5px ' + hexToRing(color), borderRadius: '3px' } : {};
		const inkData = { 'sugg-id': s.id };

		function decorateInk(span) {
			span.style.setProperty('--sugg-ring', hexToRing(color, 0.22));
			span.addEventListener('mouseenter', () => linkHover(s.id, true));
			span.addEventListener('mouseleave', () => linkHover(s.id, false));
			if (IS_AUTHOR) {
				span.addEventListener('mouseenter', () => showTip(s, span));
				span.addEventListener('mouseleave', scheduleTipHide);
			}
			return span;
		}

		if (IS_AUTHOR && s.status && s.status !== 'pending') {
			return renderDecidedInk(s, paraRuns, color, ring, inkData, onClick, decorateInk);
		}

		if (s.type === 'delete') {
			const span = el('span', { class: 'review-ink', data: inkData, style: Object.assign({ cursor: 'pointer' }, ring, strikeStyle(color, s.id)), onclick: onClick });
			appendRange(span, paraRuns, s.start, s.end);
			return decorateInk(span);
		}
		if (s.type === 'reword') {
			const struck = el('span', { style: strikeStyle(color, s.id) });
			appendRange(struck, paraRuns, s.start, s.end);
			return decorateInk(el('span', { class: 'review-ink', data: inkData, style: Object.assign({ cursor: 'pointer' }, ring), onclick: onClick },
				struck,
				el('span', { 'data-ins': '1', style: { fontFamily: CAVEAT, fontSize: '1.15em', fontWeight: '600', color: color } }, ' ' + (s.replacement || ''))
			));
		}
		if (s.type === 'insert') {
			return decorateInk(el('span', { class: 'review-ink', data: inkData, style: Object.assign({ cursor: 'pointer' }, ring), onclick: onClick },
				el('span', { 'data-ins': '1', style: { color: color, fontWeight: '700' } }, '‸'),
				el('span', { 'data-ins': '1', style: { fontFamily: CAVEAT, fontSize: '1.15em', fontWeight: '600', color: color } }, s.replacement || '')
			));
		}
		// comment: yellow marker swipe
		const span = el('span', {
			class: 'review-ink', data: inkData, onclick: onClick,
			style: Object.assign({ cursor: 'pointer', background: 'rgba(254,240,138,0.85)', borderRadius: '2px' }, ring),
		});
		appendRange(span, paraRuns, s.start, s.end);
		return decorateInk(span);
	}

	/**
	 * Author mode: a decided suggestion previews its outcome in the manuscript —
	 * accepted edits read as the revised text, rejected ones restore the original
	 * with a faint dotted memory of the ink.
	 */
	function renderDecidedInk(s, paraRuns, color, ring, inkData, onClick, decorateInk) {
		const span = el('span', {
			class: 'review-ink', data: inkData,
			style: Object.assign({ cursor: 'pointer' }, ring),
			onclick: onClick,
		});
		if (s.type === 'comment') {
			// resolved comment: the marker swipe fades to gray
			span.style.background = 'rgba(168,162,158,0.28)';
			span.style.borderRadius = '2px';
			appendRange(span, paraRuns, s.start, s.end);
			return decorateInk(span);
		}
		if (s.status === 'accepted') {
			if (s.type === 'delete') {
				const struck = el('span', { class: 'review-ink--ghost', style: strikeStyle(color, s.id) });
				appendRange(struck, paraRuns, s.start, s.end);
				span.appendChild(struck);
				return decorateInk(span);
			}
			// reword/insert: the replacement reads as real text, softly tinted
			span.appendChild(el('span', { class: 'review-ink--applied' }, s.replacement || ''));
			return decorateInk(span);
		}
		// rejected: the original stands
		if (s.type === 'insert') {
			span.appendChild(el('span', { class: 'review-ink--ghost', style: { color: color, fontWeight: '700' } }, '‸'));
			return decorateInk(span);
		}
		const orig = el('span', { class: 'review-ink--rejected' });
		appendRange(orig, paraRuns, s.start, s.end);
		span.appendChild(orig);
		return decorateInk(span);
	}

	/** Open the suggestion's form popup over its ink, prefilled for editing. */
	function editSuggestion(s) {
		if (LOCKED) {
			selectSuggestion(s, 'card');
			return;
		}
		activeSugg = s.id;
		popup = null;
		render();
		const ink = manuscriptEl.querySelector('[data-sugg-id="' + s.id + '"]');
		if (!ink) return;
		const mode = s.type === 'delete' ? 'reason' : s.type;
		popup = {
			para: s.paragraph, start: s.start, end: s.end, mode: mode, editing: s,
			draft: mode === 'comment' ? (s.reason || '') : (s.replacement || ''),
			reason: s.reason || '',
		};
		const rect = ink.getBoundingClientRect();
		const crect = manuscriptEl.getBoundingClientRect();
		popup.top = rect.top - crect.top - 10;
		popup.left = Math.max(120, Math.min(rect.left + rect.width / 2 - crect.left, crect.width - 120));
		renderPopup();
	}

	function buildGutter() {
		const gutter = el('div', { class: 'review-gutter' });
		gutter.appendChild(el('h3', { class: 'review-gutter__title' }, S.suggestionsTitle));
		if (CAN_DECIDE) {
			const pendingEdits = suggsForScene().filter((s) => s.status === 'pending' && s.type !== 'comment');
			if (pendingEdits.length > 0) {
				gutter.appendChild(el('div', { class: 'review-gutter__bulk' },
					el('button', {
						class: 'review-bulk-btn review-bulk-btn--accept', type: 'button',
						onclick: () => decideAll('accepted'),
					}, icon('fa-check'), ' ' + S.acceptAll),
					el('button', {
						class: 'review-bulk-btn review-bulk-btn--reject', type: 'button',
						onclick: () => decideAll('rejected'),
					}, icon('fa-xmark'), ' ' + S.rejectAll)));
			}
		}
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
		const decidedClass = IS_AUTHOR && s.status !== 'pending' ? ' review-card--' + s.status : '';
		const card = el('div', {
			class: 'review-card' + (active ? ' review-card--active' : '') + decidedClass,
			data: { 'card-id': s.id },
			style: { borderColor: active ? color : '' },
			onclick: () => { selectSuggestion(s, 'card'); },
		});
		card.style.setProperty('--sugg-ring', hexToRing(color, 0.22));
		card.addEventListener('mouseenter', () => linkHover(s.id, true));
		card.addEventListener('mouseleave', () => linkHover(s.id, false));
		const head = el('div', { class: 'review-card__head' },
			el('span', { class: 'review-card__type', style: { color: color } }, icon(TYPE_ICON[s.type]), ' ' + TYPE_LABEL[s.type]));
		if (!LOCKED) {
			const actions = el('span', { class: 'review-card__actions' },
				el('button', {
					class: 'review-card__action', title: S.edit, type: 'button',
					onclick: (ev) => { ev.stopPropagation(); editSuggestion(s); },
				}, icon('fa-pen')),
				el('button', {
					class: 'review-card__action review-card__action--remove', title: S.remove, type: 'button',
					onclick: (ev) => { ev.stopPropagation(); doRemove(s); },
				}, icon('fa-trash-can')));
			head.appendChild(actions);
		}
		card.appendChild(head);
		if (s.type !== 'insert' && s.original) card.appendChild(el('div', { class: 'review-card__quote' }, '“' + s.original + '”'));
		if ((s.type === 'reword' || s.type === 'insert') && s.replacement) {
			card.appendChild(el('div', { class: 'review-card__replacement', style: { color: color } },
				icon('fa-arrow-right'), ' ' + s.replacement.trim()));
		}
		if (s.reason) card.appendChild(el('div', { class: 'review-card__reason' }, s.reason));
		if (IS_AUTHOR) card.appendChild(buildDecision(s));
		return card;
	}

	/* ---------- author decisions ---------- */
	function buildDecision(s) {
		const wrap = el('div', { class: 'review-card__decide' });
		if (s.status === 'pending') {
			if (!CAN_DECIDE) return wrap;
			if (s.type === 'comment') {
				wrap.appendChild(decideBtn(S.resolve, 'fa-check', 'resolved', s, 'accept'));
			} else {
				wrap.appendChild(decideBtn(S.accept, 'fa-check', 'accepted', s, 'accept'));
				wrap.appendChild(decideBtn(S.reject, 'fa-xmark', 'rejected', s, 'reject'));
			}
			return wrap;
		}
		const label = s.status === 'accepted' ? S.chipAccepted
			: s.status === 'rejected' ? S.chipRejected
			: S.chipResolved;
		const chip = el('span', { class: 'review-chip review-chip--' + s.status },
			icon(s.status === 'rejected' ? 'fa-xmark' : 'fa-check'), ' ' + label);
		if (CAN_DECIDE) {
			chip.appendChild(el('button', {
				class: 'review-chip__undo', type: 'button', title: S.undo,
				onclick: (ev) => { ev.stopPropagation(); setStatus(s, 'pending'); },
			}, icon('fa-rotate-left')));
		}
		wrap.appendChild(chip);
		return wrap;
	}

	function decideBtn(label, ic, status, s, kind) {
		return el('button', {
			class: 'review-decide-btn review-decide-btn--' + kind, type: 'button',
			onclick: (ev) => { ev.stopPropagation(); setStatus(s, status); },
		}, icon(ic), ' ' + label);
	}

	async function setStatus(s, status) {
		try {
			const res = await fetch(BASE + '/suggestions/' + s.id + '/status', {
				method: 'POST',
				headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
				body: new URLSearchParams({ status: status }),
			});
			if (!res.ok) { toastError(); return false; }
			const updated = await res.json();
			s.status = updated.status;
			render();
			return true;
		} catch (e) { toastError(); return false; }
	}

	async function decideAll(status) {
		const pend = suggsForScene().filter((x) => x.status === 'pending' && x.type !== 'comment');
		for (const s of pend) {
			if (!(await setStatus(s, status))) break;
		}
	}

	/* ---------- selection / popup ---------- */
	// Document-level so a drag that releases outside the manuscript still works.
	function onTextMouseUp(e) {
		if (LOCKED) return;
		if (e.target.closest && e.target.closest('[data-popup]')) return;
		const onInk = e.target.closest && e.target.closest('[data-ins]');
		const inManuscript = e.target.closest && e.target.closest('.review-manuscript');
		setTimeout(() => {
			const sel = window.getSelection();
			if (!sel || sel.rangeCount === 0 || sel.isCollapsed) {
				if (inManuscript) placeCaret(sel, onInk);
				return;
			}
			const range = sel.getRangeAt(0);
			const startPt = resolvePoint(range.startContainer, range.startOffset);
			const endPt = resolvePoint(range.endContainer, range.endOffset);
			if (!startPt || !endPt) return;
			const sp = startPt.node.parentElement.closest('[data-para]');
			const ep = endPt.node.parentElement.closest('[data-para]');
			if (!sp || sp !== ep) return;
			const para = parseInt(sp.getAttribute('data-para'), 10);
			const start = srcOffsetOf(startPt.node, startPt.off);
			const end = srcOffsetOf(endPt.node, endPt.off);
			if (start == null || end == null || end <= start) return;
			if (overlapsAny(start, end, suggsForPara(para))) return;
			openPopup({ para: para, start: start, end: end, text: paraTextOf(para).slice(start, end), mode: 'menu' }, range.getBoundingClientRect());
		}, 10);
	}
	if (!LOCKED) document.addEventListener('mouseup', onTextMouseUp);

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
		showInsertCaret(para, pos);
	}

	/* ---------- insertion caret marker ---------- */
	// A caret overlaid on the text while the insert flow is active, so the exact
	// landing spot is unambiguous. Positioned from a collapsed Range's rect —
	// the manuscript DOM itself is never touched.
	let caretMarker = null;

	function removeInsertCaret() {
		if (caretMarker) { caretMarker.remove(); caretMarker = null; }
	}

	function showInsertCaret(paraIndex, pos) {
		removeInsertCaret();
		const pe = manuscriptEl.querySelector('[data-para="' + paraIndex + '"]');
		if (!pe) return;
		const walker = document.createTreeWalker(pe, NodeFilter.SHOW_TEXT);
		let n;
		while ((n = walker.nextNode())) {
			if (!nodeSrcStart.has(n)) continue;
			const s = nodeSrcStart.get(n);
			const e = s + n.nodeValue.length;
			if (pos < s || pos > e) continue;
			const range = document.createRange();
			range.setStart(n, pos - s);
			range.collapse(true);
			const rect = range.getBoundingClientRect();
			if (!rect || (rect.top === 0 && rect.height === 0)) return;
			const crect = manuscriptEl.getBoundingClientRect();
			caretMarker = el('span', { class: 'review-caret' });
			caretMarker.style.left = (rect.left - crect.left - 1) + 'px';
			caretMarker.style.top = (rect.top - crect.top) + 'px';
			caretMarker.style.height = rect.height + 'px';
			manuscriptEl.appendChild(caretMarker);
			return;
		}
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
	function closePopup() {
		removeInsertCaret();
		if (popup) { popup = null; renderPopup(); }
	}

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
				menuBtn(S.delete, 'fa-strikethrough', () => { popup.mode = 'reason'; renderPopup(); }),
				menuBtn(S.insert, 'fa-plus', () => {
					popup.mode = 'insert';
					renderPopup();
					showInsertCaret(popup.para, popup.end);
				}),
				menuBtn(S.comment, 'fa-comment', () => { popup.mode = 'comment'; renderPopup(); })
			);
		}
		// form modes: reword / insert / comment, plus 'reason' (editing a delete's reason)
		const isReasonOnly = popup.mode === 'reason';
		const title = popup.mode === 'reword' ? S.replacementLabel
			: popup.mode === 'insert' ? S.insertLabel
			: popup.mode === 'comment' ? S.commentLabel
			: S.reasonLabel;

		function save() {
			if (!isReasonOnly && !popup.draft.trim()) return;
			if (popup.editing) {
				const replacement = isReasonOnly ? null
					: popup.mode === 'comment' ? null : popup.draft;
				const reason = popup.mode === 'comment' ? popup.draft.trim() : (popup.reason || '').trim();
				commitEdit(popup.editing, replacement, reason);
			} else if (isReasonOnly) {
				commit('delete', '', (popup.reason || '').trim());
			} else if (popup.mode === 'comment') {
				commit('comment', '', popup.draft.trim());
			} else {
				commit(popup.mode, popup.draft, (popup.reason || '').trim());
			}
		}

		const saveBtn = el('button', {
			class: 'btn btn--accent review-popup__save', type: 'button', onclick: save,
		}, S.save);
		saveBtn.disabled = !isReasonOnly && !popup.draft.trim();

		const wrap = el('div', { 'data-popup': '1', class: 'review-popup review-popup--form', style: pos },
			el('div', { class: 'review-popup__label' }, title));

		let focusTarget;
		if (!isReasonOnly) {
			const textarea = el('textarea', {
				class: 'review-popup__textarea',
				placeholder: popup.mode === 'comment' ? S.commentPlaceholder : S.typePlaceholder,
				oninput: (e) => { popup.draft = e.target.value; saveBtn.disabled = !popup.draft.trim(); },
			});
			textarea.value = popup.draft || '';
			wrap.appendChild(textarea);
			focusTarget = textarea;
		}
		if (popup.mode !== 'comment') {
			const reasonInput = el('input', {
				class: 'review-popup__reason', placeholder: S.reasonPlaceholder,
				oninput: (e) => { popup.reason = e.target.value; },
			});
			reasonInput.value = popup.reason || '';
			wrap.appendChild(reasonInput);
			if (isReasonOnly) focusTarget = reasonInput;
		}
		wrap.appendChild(el('div', { class: 'review-popup__actions' },
			el('button', { class: 'btn btn--ghost', type: 'button', onclick: closePopup }, S.cancel), saveBtn));
		if (focusTarget) setTimeout(() => focusTarget.focus(), 0);
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

	async function commitEdit(s, replacement, reason) {
		closePopup();
		const body = new URLSearchParams();
		if (replacement != null) body.set('replacement', replacement);
		if (reason != null) body.set('reason', reason);
		try {
			const res = await fetch('/review/' + TOKEN + '/suggestions/' + s.id, {
				method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body,
			});
			if (!res.ok) { toastError(); return; }
			const updated = await res.json();
			const list = scene().suggestions;
			const i = list.findIndex((x) => x.id === s.id);
			if (i !== -1) list[i] = toClient(updated);
			render();
		} catch (e) { toastError(); }
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
		btn.addEventListener('click', showSubmitDialog);
	}

	function closeSubmitDialog() {
		const overlay = document.getElementById('review-submit-dialog');
		if (overlay) overlay.remove();
	}

	function showSubmitDialog() {
		closeSubmitDialog();
		const total = DATA.scenes.reduce((acc, sc) => acc + sc.suggestions.length, 0);
		const tally = total === 1 ? S.submitTallyOne : S.submitTallyMany.replace('{0}', String(total));
		const body = S.submitBody.replace('{0}', tally).replace('{1}', DATA.authorName || '');

		const submitBtn = el('button', {
			class: 'btn btn--accent', type: 'button',
			onclick: async () => {
				submitBtn.disabled = true;
				try {
					const res = await fetch('/review/' + TOKEN + '/submit', { method: 'POST' });
					if (res.ok) { window.location.reload(); return; }
				} catch (e) { /* fallthrough */ }
				submitBtn.disabled = false;
				toastError();
			},
		}, icon('fa-paper-plane'), ' ' + S.submitAction);

		const overlay = el('div', {
			class: 'dialog-overlay', id: 'review-submit-dialog',
			onclick: (e) => { if (e.target === e.currentTarget) closeSubmitDialog(); },
		},
			el('div', { class: 'dialog' },
				el('div', { class: 'dialog__header' },
					el('h3', {}, S.submitTitle),
					el('button', { class: 'dialog__close', type: 'button', onclick: closeSubmitDialog }, icon('fa-times'))),
				el('div', { class: 'dialog__body review-submit-dialog__body' }, body),
				el('div', { class: 'dialog__actions' },
					el('button', { class: 'btn btn--ghost', type: 'button', onclick: closeSubmitDialog }, S.cancel),
					submitBtn)));
		document.body.appendChild(overlay);
	}

	/* ---------- first-open welcome ---------- */
	// Shown once, the first time the editor's link is ever opened: what this is,
	// how to mark up, and that nothing reaches the author until Submit Revisions.
	function closeWelcome() {
		const overlay = document.getElementById('review-welcome-dialog');
		if (overlay) overlay.remove();
	}

	function showWelcomeDialog() {
		const tally = DATA.scenes.length === 1
			? S.sceneTallyOne
			: S.sceneTallyMany.replace('{0}', String(DATA.scenes.length));
		const overlay = el('div', {
			class: 'dialog-overlay', id: 'review-welcome-dialog',
			onclick: (e) => { if (e.target === e.currentTarget) closeWelcome(); },
		},
			el('div', { class: 'dialog' },
				el('div', { class: 'dialog__header' },
					el('h3', {}, S.welcomeTitle),
					el('button', { class: 'dialog__close', type: 'button', onclick: closeWelcome }, icon('fa-times'))),
				el('div', { class: 'dialog__body review-submit-dialog__body' },
					el('p', {}, S.welcomeIntro.replace('{0}', DATA.authorName || '').replace('{1}', tally)),
					el('p', {}, S.welcomeHow),
					el('p', { class: 'review-welcome__note' },
						icon('fa-paper-plane'), ' ' + S.welcomeSubmitNote.replace('{0}', S.submit))),
				el('div', { class: 'dialog__actions' },
					el('button', { class: 'btn btn--accent', type: 'button', onclick: closeWelcome },
						icon('fa-pen-nib'), ' ' + S.welcomeAction))));
		document.body.appendChild(overlay);
	}

	/* ---------- author ink tooltip ---------- */
	// Hovering ink surfaces the reviewer's note — and quick accept/reject while
	// the suggestion is pending — right at the text, no glance to the gutter.
	// The tooltip survives the mouse crossing over to it; a short grace timer
	// hides it once both the ink and the tooltip are left.
	let tip = null;
	let tipHideTimer = null;

	function removeTip() {
		clearTimeout(tipHideTimer);
		tipHideTimer = null;
		if (tip) { tip.remove(); tip = null; }
	}

	function scheduleTipHide() {
		clearTimeout(tipHideTimer);
		tipHideTimer = setTimeout(removeTip, 250);
	}

	function showTip(s, ink) {
		const pendingActions = CAN_DECIDE && s.status === 'pending';
		if (!s.reason && !pendingActions) return;
		if (tip && tip.getAttribute('data-tip-id') === String(s.id)) {
			clearTimeout(tipHideTimer);
			return;
		}
		removeTip();

		const wrap = el('div', { class: 'review-tip', data: { 'tip-id': s.id } });
		if (s.reason) wrap.appendChild(el('div', { class: 'review-tip__reason' }, s.reason));
		if (pendingActions) {
			const actions = el('div', { class: 'review-tip__actions' });
			if (s.type === 'comment') {
				actions.appendChild(decideBtn(S.resolve, 'fa-check', 'resolved', s, 'accept'));
			} else {
				actions.appendChild(decideBtn(S.accept, 'fa-check', 'accepted', s, 'accept'));
				actions.appendChild(decideBtn(S.reject, 'fa-xmark', 'rejected', s, 'reject'));
			}
			wrap.appendChild(actions);
		}
		wrap.addEventListener('mouseenter', () => clearTimeout(tipHideTimer));
		wrap.addEventListener('mouseleave', scheduleTipHide);

		const rect = ink.getBoundingClientRect();
		const crect = manuscriptEl.getBoundingClientRect();
		wrap.style.top = (rect.top - crect.top - 6) + 'px';
		wrap.style.left = Math.max(130, Math.min(rect.left + rect.width / 2 - crect.left, crect.width - 130)) + 'px';
		manuscriptEl.appendChild(wrap);
		tip = wrap;
	}

	/* ---------- author commit ---------- */
	function initCommit() {
		const btn = document.getElementById('review-commit-btn');
		if (!btn) return;
		btn.addEventListener('click', showCommitDialog);
	}

	let commitDone = false;

	function closeCommitDialog() {
		if (commitDone) { window.location.reload(); return; }
		const overlay = document.getElementById('review-commit-dialog');
		if (overlay) overlay.remove();
	}

	function showCommitDialog() {
		closeCommitDialog();
		const edits = [];
		DATA.scenes.forEach((sc) => sc.suggestions.forEach((s) => { if (s.type !== 'comment') edits.push(s); }));
		const count = (status) => edits.filter((s) => s.status === status).length;
		const pending = count('pending');
		const tallyParts = [
			S.commitTallyAccepted.replace('{0}', String(count('accepted'))),
			S.commitTallyRejected.replace('{0}', String(count('rejected'))),
		];
		if (pending > 0) tallyParts.push(S.commitTallyPending.replace('{0}', String(pending)));

		const body = el('div', { class: 'dialog__body review-submit-dialog__body' },
			el('div', { class: 'review-commit-tally' }, tallyParts.join(' · ')),
			el('p', {}, S.commitBody));
		if (pending > 0) {
			body.appendChild(el('p', { class: 'review-commit-warning' },
				icon('fa-triangle-exclamation'), ' ' + S.commitPendingWarning));
		}

		const commitBtn = el('button', {
			class: 'btn btn--accent', type: 'button',
			onclick: async () => {
				commitBtn.disabled = true;
				try {
					const res = await fetch(BASE + '/commit', { method: 'POST' });
					if (res.ok) {
						commitDone = true;
						renderCommitResult(dialog, await res.json());
						return;
					}
				} catch (e) { /* fallthrough */ }
				closeCommitDialog();
				toastError(S.commitFailed);
			},
		}, icon('fa-stamp'), ' ' + S.commitAction);

		const dialog = el('div', { class: 'dialog' },
			el('div', { class: 'dialog__header' },
				el('h3', {}, S.commitTitle),
				el('button', { class: 'dialog__close', type: 'button', onclick: closeCommitDialog }, icon('fa-times'))),
			body,
			el('div', { class: 'dialog__actions' },
				el('button', { class: 'btn btn--ghost', type: 'button', onclick: closeCommitDialog }, S.cancel),
				commitBtn));
		const overlay = el('div', {
			class: 'dialog-overlay', id: 'review-commit-dialog',
			onclick: (e) => { if (e.target === e.currentTarget) closeCommitDialog(); },
		}, dialog);
		document.body.appendChild(overlay);
	}

	/** Swap the confirm dialog's content for the per-scene outcome report. */
	function renderCommitResult(dialog, result) {
		const OUTCOME_TEXT = {
			applied: S.outcomeApplied,
			diverged: S.outcomeDiverged,
			unchanged: S.outcomeUnchanged,
			scene_missing: S.outcomeSceneMissing,
		};
		const OUTCOME_ICON = {
			applied: 'fa-check',
			diverged: 'fa-code-branch',
			unchanged: 'fa-minus',
			scene_missing: 'fa-triangle-exclamation',
		};
		dialog.innerHTML = '';
		dialog.appendChild(el('div', { class: 'dialog__header' }, el('h3', {}, S.commitResultTitle)));

		const body = el('div', { class: 'dialog__body review-submit-dialog__body' });
		const draftMade = result.scenes.some((sc) => sc.outcome === 'applied' || sc.outcome === 'diverged');
		if (draftMade) {
			body.appendChild(el('p', { class: 'review-commit-draft' },
				icon('fa-file-pen'), ' ' + S.commitResultDraft.replace('{0}', result.draftName)));
		}
		const list = el('div', { class: 'review-commit-outcomes' });
		result.scenes.forEach((sc) => {
			list.appendChild(el('div', { class: 'review-commit-outcome review-commit-outcome--' + sc.outcome },
				el('span', { class: 'review-commit-outcome__scene' }, icon(OUTCOME_ICON[sc.outcome] || 'fa-minus'), ' ' + sc.sceneName),
				el('span', { class: 'review-commit-outcome__text' }, OUTCOME_TEXT[sc.outcome] || sc.outcome)));
		});
		body.appendChild(list);
		dialog.appendChild(body);

		dialog.appendChild(el('div', { class: 'dialog__actions' },
			el('button', {
				class: 'btn btn--accent', type: 'button',
				onclick: () => { window.location.reload(); },
			}, S.commitDone)));
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
	function hexToRing(hex, alpha) {
		const n = parseInt(hex.slice(1), 16);
		return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + (alpha || 0.35) + ')';
	}
	function toastError(message) {
		const container = document.getElementById('toast-container');
		if (!container) return;
		const t = el('div', { class: 'toast toast-error', role: 'alert' },
			el('span', { class: 'toast-message' }, message || S.saveFailed),
			el('button', { class: 'toast-dismiss', onclick: function () { t.remove(); } }, '×'));
		container.appendChild(t);
		setTimeout(() => t.remove(), 5000);
	}

	document.addEventListener('keydown', (e) => {
		if (e.key === 'Escape') { closePopup(); closeSubmitDialog(); closeCommitDialog(); closeWelcome(); }
	});

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
	initCommit();
	if (!IS_AUTHOR && !LOCKED && DATA.firstOpen) showWelcomeDialog();
})();
