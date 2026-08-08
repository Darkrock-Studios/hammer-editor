package com.darkrockstudios.apps.hammer.common.storyeditor.drafts

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.diff.DiffKind
import com.darkrockstudios.apps.hammer.base.diff.DiffResult
import com.darkrockstudios.apps.hammer.base.diff.DiffSpan
import com.darkrockstudios.apps.hammer.base.diff.OffsetMap
import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftCompare
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.loadSceneContent
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.sceneDiffText
import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.TextEditor
import com.darkrockstudios.texteditor.TextEditorRange
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.richstyle.HighlightSpanStyle
import com.darkrockstudios.texteditor.richstyle.RichSpan
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.rememberTextEditorState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val PANE_DRAFT = 0
private const val PANE_CURRENT = 1

/** How long after the last edit before the right pane re-runs the diff. */
private const val DIFF_RECOMPUTE_DELAY_MS = 500L

/** Background alpha for diff highlights — light enough to read text through. */
private const val DIFF_HIGHLIGHT_ALPHA = 0.28f

@Composable
fun DraftCompareUi(component: DraftCompare) {
	val screen = LocalScreenCharacteristic.current
	val state by component.state.subscribeAsState()
	val draftName = component.draftDef.draftName
	val markdownConfig = LocalMarkdownConfig.current

	// Hoisted above the width branch: the two layouts are separate composables, so building
	// the editors inside them would discard the user's merge edits on a resize or rotate
	// while component.mergedContent still pointed at the torn-down editor.
	val draftMarkdown = key(state.draftContent) {
		rememberSceneContentEditor(state.draftContent, markdownConfig)
	}
	val currentMarkdown = key(state.sceneContent) {
		rememberSceneContentEditor(state.sceneContent, markdownConfig)
	}

	Column(modifier = Modifier.fillMaxSize()) {
		HdMasthead(
			section = "DRAFT COMPARE",
			leadingMeta = listOf(draftName),
			trailing = {
				HdMastheadAction(
					label = if (state.showDiff) "DIFF: ON" else "DIFF: OFF",
					onClick = { component.setShowDiff(!state.showDiff) },
				)
				if (screen.needsExplicitClose) {
					HdMastheadAction(label = "× CLOSE", onClick = component::cancel)
				}
			},
		)
		HdFolioDivider()

		when (screen.windowWidthClass) {
			WindowWidthSizeClass.Compact, WindowWidthSizeClass.Medium -> {
				CompactDraftCompareUi(
					modifier = Modifier.fillMaxSize(),
					component = component,
					draftMarkdown = draftMarkdown,
					currentMarkdown = currentMarkdown,
				)
			}

			else -> {
				ExpandedDraftCompareUi(
					modifier = Modifier.fillMaxSize(),
					component = component,
					draftMarkdown = draftMarkdown,
					currentMarkdown = currentMarkdown,
				)
			}
		}
	}
}

@Composable
private fun CompactDraftCompareUi(
	modifier: Modifier,
	component: DraftCompare,
	draftMarkdown: MarkdownExtension,
	currentMarkdown: MarkdownExtension,
) {
	var pane by rememberSaveable { mutableIntStateOf(PANE_DRAFT) }
	val draftLabel = Res.string.draft_compare_tab_title_draft.get()
	val currentLabel = Res.string.draft_compare_tab_title_current.get()

	// Only one pane is composed at a time here, so the panes' own seeding effects can't
	// be relied on to feed both sides of the diff.
	LaunchedEffect(draftMarkdown) {
		component.submitDraftText(sceneDiffText(draftMarkdown))
	}
	LaunchedEffect(currentMarkdown) {
		component.onCurrentTextChanged(sceneDiffText(currentMarkdown))
	}

	Column(modifier = modifier) {
		HdHairlineSegmentedPicker(
			options = listOf(PANE_DRAFT, PANE_CURRENT),
			selected = pane,
			onSelect = { pane = it },
			label = { if (it == PANE_DRAFT) draftLabel else currentLabel },
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		)
		// Only one pane is on screen at a time, so there's nothing to scroll-sync here.
		if (pane == PANE_DRAFT) {
			DraftPane(
				modifier = Modifier.fillMaxSize(),
				sectionNumber = 1,
				component = component,
				markdownExtension = draftMarkdown,
			)
		} else {
			CurrentPane(
				modifier = Modifier.fillMaxSize(),
				sectionNumber = 1,
				component = component,
				markdownExtension = currentMarkdown,
			)
		}
	}
}

@Composable
private fun ExpandedDraftCompareUi(
	modifier: Modifier,
	component: DraftCompare,
	draftMarkdown: MarkdownExtension,
	currentMarkdown: MarkdownExtension,
) {
	val state by component.state.subscribeAsState()
	val draftState = draftMarkdown.editorState
	val currentState = currentMarkdown.editorState

	SyncScrolling(
		leftState = draftState,
		rightState = currentState,
		// Gate sync on the same toggle as highlights: with DIFF off, leave the user's scroll alone.
		diffResult = state.diffResult.takeIf { state.showDiff },
	)

	Row(modifier = modifier) {
		DraftPane(
			modifier = Modifier.weight(1f).fillMaxHeight(),
			sectionNumber = 1,
			component = component,
			markdownExtension = draftMarkdown,
		)
		VerticalDivider(
			color = MaterialTheme.colorScheme.outlineVariant,
			thickness = Dp.Hairline,
		)
		CurrentPane(
			modifier = Modifier.weight(1f).fillMaxHeight(),
			sectionNumber = 2,
			component = component,
			markdownExtension = currentMarkdown,
		)
	}
}

@Composable
private fun DraftPane(
	modifier: Modifier,
	sectionNumber: Int,
	component: DraftCompare,
	markdownExtension: MarkdownExtension,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	val deletedHighlight = rememberDeletedHighlight()
	val textEditorState = markdownExtension.editorState

	// The draft is read-only, so its rendered text never changes — submit it once for the diff.
	LaunchedEffect(markdownExtension) {
		component.submitDraftText(sceneDiffText(markdownExtension))
	}
	val spans = if (state.showDiff) state.diffResult?.leftSpans.orEmpty() else emptyList()
	DiffHighlightEffect(textEditorState, spans, deletedHighlight)

	var title by remember { mutableStateOf("") }
	LaunchedEffect(component.draftDef.draftName) {
		title = strRes.get(Res.string.draft_compare_draft_header, component.draftDef.draftName)
	}

	Column(
		modifier = modifier.padding(
			horizontal = Ui.Padding.XL,
			vertical = Ui.Padding.L,
		),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdSectionHeader(
			section = sectionNumber,
			title = title,
			trailing = { HdMonoLabel(text = "READ ONLY") },
		)
		Text(
			text = Res.string.draft_compare_draft_subheader.get(),
			style = MaterialTheme.typography.bodySmall,
			fontStyle = FontStyle.Italic,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		HdHairlineButton(
			label = Res.string.draft_compare_draft_accept_button.get(),
			onClick = { component.pickDraft() },
			emphasised = true,
		)
		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.padding(Ui.Padding.L),
		) {
			TextEditor(
				modifier = Modifier.fillMaxSize(),
				state = textEditorState,
				enabled = false,
			)
		}
	}
}

@Composable
private fun CurrentPane(
	modifier: Modifier,
	sectionNumber: Int,
	component: DraftCompare,
	markdownExtension: MarkdownExtension,
) {
	val state by component.state.subscribeAsState()
	val insertedHighlight = rememberInsertedHighlight()
	val textEditorState = markdownExtension.editorState

	LaunchedEffect(markdownExtension) {
		// Seed the diff with the initial text, then watch for edits. `collectLatest` cancels the
		// previous lambda when a new edit arrives, so the `delay` acts as a per-keystroke debounce:
		// the diff recompute only fires after the user stops typing for [DIFF_RECOMPUTE_DELAY_MS].
		// The merged-content update runs synchronously before the delay so picking the current
		// draft always uses the latest text. Submitting the editor's rendered text (not the
		// markdown) keeps the diff in the same coordinate space the highlights are drawn in.
		component.onCurrentTextChanged(sceneDiffText(markdownExtension))
		textEditorState.editOperations.collectLatest { _ ->
			component.onMergedContentChanged(ComposeRichText(markdownExtension))
			delay(DIFF_RECOMPUTE_DELAY_MS)
			component.onCurrentTextChanged(sceneDiffText(markdownExtension))
		}
	}

	val spans = if (state.showDiff) state.diffResult?.rightSpans.orEmpty() else emptyList()
	DiffHighlightEffect(textEditorState, spans, insertedHighlight)

	Column(
		modifier = modifier.padding(
			horizontal = Ui.Padding.XL,
			vertical = Ui.Padding.L,
		),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdSectionHeader(
			section = sectionNumber,
			title = Res.string.draft_compare_current_header.get(),
			trailing = { HdMonoLabel(text = "EDITABLE") },
		)
		Text(
			text = Res.string.draft_compare_current_subheader.get(),
			style = MaterialTheme.typography.bodySmall,
			fontStyle = FontStyle.Italic,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		HdHairlineButton(
			label = Res.string.draft_compare_current_accept_button.get(),
			onClick = { component.pickMerged() },
			emphasised = true,
		)
		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.padding(Ui.Padding.L),
		) {
			TextEditor(
				modifier = Modifier.fillMaxSize(),
				state = textEditorState,
			)
		}
	}
}

/** An editor seeded with [content], keeping the block structure markdown carries. */
@Composable
private fun rememberSceneContentEditor(
	content: SceneContent?,
	markdownConfig: MarkdownConfiguration,
): MarkdownExtension {
	val editorState = rememberTextEditorState()
	val markdownExtension = remember(editorState) {
		editorState.withMarkdown(markdownConfig).also { loadSceneContent(it, content) }
	}
	// Both panes restyle together; the config is otherwise baked in at import time and
	// the two would drift apart on a font size or theme change.
	LaunchedEffect(markdownExtension, markdownConfig) {
		markdownExtension.updateMarkdownConfiguration(markdownConfig)
	}
	return markdownExtension
}

@Composable
private fun rememberDeletedHighlight(): HighlightSpanStyle {
	val danger = LocalHammerColors.current.danger
	return remember(danger) { HighlightSpanStyle(danger.copy(alpha = DIFF_HIGHLIGHT_ALPHA)) }
}

@Composable
private fun rememberInsertedHighlight(): HighlightSpanStyle {
	val success = LocalHammerColors.current.success
	return remember(success) { HighlightSpanStyle(success.copy(alpha = DIFF_HIGHLIGHT_ALPHA)) }
}

@Composable
private fun rememberMovedHighlight(): HighlightSpanStyle {
	val moved = LocalHammerColors.current.moved
	return remember(moved) { HighlightSpanStyle(moved.copy(alpha = DIFF_HIGHLIGHT_ALPHA)) }
}

/**
 * Paint one pane's diff [spans] onto its editor: plain deletions/insertions in [baseStyle] and
 * relocated paragraphs in the shared moved style. Each kind is tracked independently so a theme
 * change re-applies cleanly without orphaning the prior pass's spans.
 */
@Composable
private fun DiffHighlightEffect(
	editorState: TextEditorState,
	spans: List<DiffSpan>,
	baseStyle: HighlightSpanStyle,
) {
	val movedStyle = rememberMovedHighlight()
	var appliedBase by remember(editorState) { mutableStateOf<HighlightSpanStyle?>(null) }
	var appliedMoved by remember(editorState) { mutableStateOf<HighlightSpanStyle?>(null) }
	LaunchedEffect(spans, editorState, baseStyle, movedStyle) {
		applyDiffHighlights(
			editorState = editorState,
			spans = spans.filter { it.kind != DiffKind.MOVED },
			style = baseStyle,
			previousStyle = appliedBase,
		)
		applyDiffHighlights(
			editorState = editorState,
			spans = spans.filter { it.kind == DiffKind.MOVED },
			style = movedStyle,
			previousStyle = appliedMoved,
		)
		appliedBase = baseStyle
		appliedMoved = movedStyle
	}
}

/**
 * Replace the diff highlights drawn with [style] on this editor.
 *
 * Rich spans are a non-destructive draw overlay: they don't emit edit operations (so applying
 * them doesn't retrigger the edit watcher) and aren't serialized into the saved content. We
 * remove the prior spans by matching the exact style instance — the manager shifts span ranges
 * to track edits, so range-based removal wouldn't find them, but the style identity is stable.
 *
 * The highlight style is recreated when the theme color changes, so we match both the [style]
 * we're about to apply and the [previousStyle] from the last pass; otherwise a theme change mid-
 * compare would orphan the old-instance spans and they'd accumulate on top of the new ones.
 *
 * Span offsets are clamped to the editor's current text so a diff computed against a slightly
 * older revision (during the debounce window) can't produce an out-of-range span.
 */
private fun applyDiffHighlights(
	editorState: TextEditorState,
	spans: List<DiffSpan>,
	style: HighlightSpanStyle,
	previousStyle: HighlightSpanStyle?,
) {
	val toRemove = editorState.richSpanManager.getAllRichSpans()
		.filter { it.style === style || it.style === previousStyle }

	val text = editorState.getAllText().text
	val length = text.length
	val toAdd = if (length == 0) {
		emptyList()
	} else {
		spans.mapNotNull { span ->
			val start = span.range.start.coerceIn(0, length)
			val end = span.range.endExclusive.coerceIn(start, length)
			if (end <= start) return@mapNotNull null
			RichSpan(
				range = TextEditorRange(
					start = offsetToCharLine(text, start),
					end = offsetToCharLine(text, end),
				),
				style = style,
			)
		}
	}

	// Diff highlights are ephemeral overlays: route them through the batch
	// overlay API so they stay out of undo history and the edit stream and
	// relayout once instead of per span.
	editorState.updateRichSpans(remove = toRemove, add = toAdd)
}

private fun offsetToCharLine(text: String, offset: Int): CharLineOffset {
	val safe = offset.coerceIn(0, text.length)
	var line = 0
	var lineStart = 0
	for (i in 0 until safe) {
		if (text[i] == '\n') {
			line++
			lineStart = i + 1
		}
	}
	return CharLineOffset(line, safe - lineStart)
}

/**
 * Tracks the scroll-pixel values we set programmatically on each pane, so the follow-scroll's
 * echo emission is swallowed instead of bouncing back. A set (not a single slot) is used because
 * rapid scrolling pipelines several commands before their echoes arrive.
 */
private class ScrollSyncGuard {
	val pendingLeft = HashSet<Int>()
	val pendingRight = HashSet<Int>()
}

/**
 * Keep two editor panes scrolled to matching prose using the diff anchors.
 *
 * Driven off the raw scroll-pixel value (Compose-observable). When one pane moves, its top-visible
 * offset is mapped through [OffsetMap] to the other pane's corresponding offset and converted back
 * to a pixel position, which we apply with the synchronous [TextEditorScrollState.scrollTo] so we
 * can read the exact landed value and mark it pending — its echo is then ignored, breaking the
 * feedback loop. (`scrollToPosition(offset)` normalizes to the wrapped-line start, so an
 * offset-equality guard never matched and the panes fought each other.)
 *
 * No-ops when [diffResult] is null (diff off, or not yet computed). Mapping uses each editor's
 * current text, so right-pane alignment can drift slightly while the user is mid-edit until the
 * debounced recompute lands — same staleness window as the highlights.
 */
@Composable
private fun SyncScrolling(
	leftState: TextEditorState,
	rightState: TextEditorState,
	diffResult: DiffResult?,
) {
	if (diffResult == null) return
	val offsetMap = remember(diffResult) { OffsetMap(diffResult.anchors) }
	val guard = remember(leftState, rightState) { ScrollSyncGuard() }

	// Prefix-sum line offsets so the per-scroll-event offset<->(line,char) conversions are O(1)/
	// O(log n) instead of walking every line on each pixel. The left pane is read-only so its
	// index is built once; the right pane's is rebuilt when the diff settles (textLines may have
	// changed) — the same staleness window the rest of the sync already accepts mid-edit.
	val leftIndex = remember(leftState) { LineOffsetIndex(leftState.textLines.map { it.length }) }
	val rightIndex = remember(rightState, diffResult) { LineOffsetIndex(rightState.textLines.map { it.length }) }

	LaunchedEffect(leftState, rightState, offsetMap, leftIndex, rightIndex) {
		snapshotFlow { leftState.scrollState.value }
			.collect { leftY ->
				if (guard.pendingLeft.remove(leftY)) return@collect
				val targetY = mapScrollTop(leftState, leftIndex, rightState, rightIndex, leftY, offsetMap::leftToRight)
				val before = rightState.scrollState.value
				if (targetY != before) {
					rightState.scrollState.scrollTo(targetY)
					val landed = rightState.scrollState.value
					if (landed != before) guard.pendingRight.add(landed)
				}
			}
	}

	LaunchedEffect(leftState, rightState, offsetMap, leftIndex, rightIndex) {
		snapshotFlow { rightState.scrollState.value }
			.collect { rightY ->
				if (guard.pendingRight.remove(rightY)) return@collect
				val targetY = mapScrollTop(rightState, rightIndex, leftState, leftIndex, rightY, offsetMap::rightToLeft)
				val before = leftState.scrollState.value
				if (targetY != before) {
					leftState.scrollState.scrollTo(targetY)
					val landed = leftState.scrollState.value
					if (landed != before) guard.pendingLeft.add(landed)
				}
			}
	}
}

/**
 * Given [fromState]'s current scroll-top pixel [fromY], find the matching scroll-top pixel for
 * [toState]: resolve the offset at the top of [fromState]'s viewport, map it to the other side
 * via [mapAbs], and convert back to a pixel position in [toState]. [fromIndex] / [toIndex] are the
 * prefix-sum line indices for each pane.
 */
private fun mapScrollTop(
	fromState: TextEditorState,
	fromIndex: LineOffsetIndex,
	toState: TextEditorState,
	toIndex: LineOffsetIndex,
	fromY: Int,
	mapAbs: (Int) -> Int,
): Int {
	val fromTop = fromState.scrollManager.offsetAtYPosition(fromY.toFloat())
	val targetAbs = mapAbs(fromIndex.absoluteOf(fromTop))
	val toTop = toIndex.charLineOf(targetAbs)
	return toState.scrollManager.calculateOffsetYPosition(toTop).toInt()
}

/**
 * Precomputed prefix sums of per-line start offsets, so converting between an absolute char offset
 * and a (line, char) position is O(1) / O(log n) instead of walking the line list. Built from the
 * editor's line lengths; rebuilt whenever those change.
 */
private class LineOffsetIndex(lineLengths: List<Int>) {
	// lineStart[i] = absolute char offset of the first char of line i (each line is followed by '\n').
	private val lineStart = IntArray(lineLengths.size)
	private val lengths = IntArray(lineLengths.size) { lineLengths[it] }

	init {
		var acc = 0
		for (i in lineLengths.indices) {
			lineStart[i] = acc
			acc += lineLengths[i] + 1
		}
	}

	/** Absolute char offset of a (line, char) position. */
	fun absoluteOf(offset: CharLineOffset): Int {
		if (lineStart.isEmpty()) return offset.char
		val line = offset.line.coerceIn(0, lineStart.size - 1)
		return lineStart[line] + offset.char
	}

	/** The (line, char) position for an absolute char [absolute] offset. */
	fun charLineOf(absolute: Int): CharLineOffset {
		if (lineStart.isEmpty()) return CharLineOffset(0, 0)
		val target = absolute.coerceAtLeast(0)
		// Binary search for the last line whose start is <= target.
		var lo = 0
		var hi = lineStart.size - 1
		while (lo < hi) {
			val mid = (lo + hi + 1) ushr 1
			if (lineStart[mid] <= target) lo = mid else hi = mid - 1
		}
		val charInLine = (target - lineStart[lo]).coerceIn(0, lengths[lo])
		return CharLineOffset(lo, charInLine)
	}
}
