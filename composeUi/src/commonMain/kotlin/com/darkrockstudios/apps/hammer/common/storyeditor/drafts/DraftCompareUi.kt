package com.darkrockstudios.apps.hammer.common.storyeditor.drafts

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.diff.DiffResult
import com.darkrockstudios.apps.hammer.base.diff.DiffSpan
import com.darkrockstudios.apps.hammer.base.diff.OffsetMap
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftCompare
import com.darkrockstudios.apps.hammer.common.compose.LocalMarkdownConfig
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.getInitialEditorContent
import com.darkrockstudios.apps.hammer.draft_compare_current_accept_button
import com.darkrockstudios.apps.hammer.draft_compare_current_header
import com.darkrockstudios.apps.hammer.draft_compare_current_subheader
import com.darkrockstudios.apps.hammer.draft_compare_draft_accept_button
import com.darkrockstudios.apps.hammer.draft_compare_draft_header
import com.darkrockstudios.apps.hammer.draft_compare_draft_subheader
import com.darkrockstudios.apps.hammer.draft_compare_tab_title_current
import com.darkrockstudios.apps.hammer.draft_compare_tab_title_draft
import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.TextEditor
import com.darkrockstudios.texteditor.TextEditorRange
import com.darkrockstudios.texteditor.markdown.withMarkdown
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
				CompactDraftCompareUi(Modifier.fillMaxSize(), component)
			}

			else -> {
				ExpandedDraftCompareUi(Modifier.fillMaxSize(), component)
			}
		}
	}
}

@Composable
private fun CompactDraftCompareUi(modifier: Modifier, component: DraftCompare) {
	var pane by rememberSaveable { mutableIntStateOf(PANE_DRAFT) }
	val state by component.state.subscribeAsState()
	val markdownConfig = LocalMarkdownConfig.current
	val draftLabel = Res.string.draft_compare_tab_title_draft.get()
	val currentLabel = Res.string.draft_compare_tab_title_current.get()

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
			val draftState = key(state.draftContent) {
				rememberTextEditorState(getInitialEditorContent(state.draftContent, markdownConfig))
			}
			DraftPane(
				modifier = Modifier.fillMaxSize(),
				sectionNumber = 1,
				component = component,
				textEditorState = draftState,
			)
		} else {
			val currentState = key(state.sceneContent) {
				rememberTextEditorState(getInitialEditorContent(state.sceneContent, markdownConfig))
			}
			CurrentPane(
				modifier = Modifier.fillMaxSize(),
				sectionNumber = 1,
				component = component,
				textEditorState = currentState,
			)
		}
	}
}

@Composable
private fun ExpandedDraftCompareUi(modifier: Modifier, component: DraftCompare) {
	val state by component.state.subscribeAsState()
	val markdownConfig = LocalMarkdownConfig.current

	// Hoist both editor states here so we can wire synchronized scrolling between the panes.
	// Each is keyed on its content so it rebuilds when the draft / scene finishes loading.
	val draftState = key(state.draftContent) {
		rememberTextEditorState(getInitialEditorContent(state.draftContent, markdownConfig))
	}
	val currentState = key(state.sceneContent) {
		rememberTextEditorState(getInitialEditorContent(state.sceneContent, markdownConfig))
	}

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
			textEditorState = draftState,
		)
		VerticalDivider(
			color = MaterialTheme.colorScheme.outlineVariant,
			thickness = Dp.Hairline,
		)
		CurrentPane(
			modifier = Modifier.weight(1f).fillMaxHeight(),
			sectionNumber = 2,
			component = component,
			textEditorState = currentState,
		)
	}
}

@Composable
private fun DraftPane(
	modifier: Modifier,
	sectionNumber: Int,
	component: DraftCompare,
	textEditorState: TextEditorState,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	val deletedStyle = rememberDeletedStyle()

	val draftSource = state.draftContent?.markdown
	LaunchedEffect(state.diffResult, state.showDiff, draftSource, textEditorState) {
		applyDiffHighlights(
			editorState = textEditorState,
			source = draftSource,
			spans = if (state.showDiff) state.diffResult?.leftSpans.orEmpty() else emptyList(),
			style = deletedStyle,
		)
	}

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
	textEditorState: TextEditorState,
) {
	val state by component.state.subscribeAsState()
	val markdownConfig = LocalMarkdownConfig.current
	val insertedStyle = rememberInsertedStyle()

	val markdownExtension = remember(textEditorState) { textEditorState.withMarkdown(markdownConfig) }

	LaunchedEffect(markdownExtension, markdownConfig) {
		markdownExtension.updateMarkdownConfiguration(markdownConfig)
	}

	LaunchedEffect(textEditorState) {
		// `collectLatest` cancels the previous lambda when a new edit arrives, so the
		// `delay` below acts as a per-keystroke debounce — the recompute only fires
		// after the user stops typing for [DIFF_RECOMPUTE_DELAY_MS]. The merged-content
		// update runs synchronously before the delay, so picking the current draft
		// always uses the latest text.
		textEditorState.editOperations.collectLatest { _ ->
			val richText = ComposeRichText(markdownExtension)
			component.onMergedContentChanged(richText)
			delay(DIFF_RECOMPUTE_DELAY_MS)
			component.onCurrentMarkdownChanged(richText.convertToMarkdown())
		}
	}

	// Source text we map diff offsets through. While the user edits, the latest source
	// markdown comes from the live markdown extension; the diff was computed against an
	// earlier snapshot, so highlights may briefly point at slightly stale ranges until
	// the debounced recompute lands and reapplies them.
	val initialSource = state.sceneContent?.markdown
	LaunchedEffect(state.diffResult, state.showDiff, initialSource, textEditorState) {
		val source = state.diffResult?.let { _ -> markdownExtension.exportAsMarkdown() }
			?: initialSource
		applyDiffHighlights(
			editorState = textEditorState,
			source = source,
			spans = if (state.showDiff) state.diffResult?.rightSpans.orEmpty() else emptyList(),
			style = insertedStyle,
		)
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

@Composable
private fun rememberDeletedStyle(): SpanStyle {
	val danger = LocalHammerColors.current.danger
	return remember(danger) {
		SpanStyle(
			background = danger.copy(alpha = DIFF_HIGHLIGHT_ALPHA),
			textDecoration = TextDecoration.LineThrough,
		)
	}
}

@Composable
private fun rememberInsertedStyle(): SpanStyle {
	val success = LocalHammerColors.current.success
	return remember(success) {
		SpanStyle(
			background = success.copy(alpha = DIFF_HIGHLIGHT_ALPHA),
			textDecoration = TextDecoration.Underline,
		)
	}
}

/**
 * Clear any previously-applied [style] across the whole document, then re-add the given [spans]
 * mapped from source offsets to editor (line, char) ranges.
 *
 * No-ops when [source] is null. Skips spans whose range falls outside the editor's current text
 * (which can happen after edits invalidate older diff results until the debounced recompute lands).
 */
private fun applyDiffHighlights(
	editorState: TextEditorState,
	source: String?,
	spans: List<DiffSpan>,
	style: SpanStyle,
) {
	if (source == null) return
	val fullRange = TextEditorRange(
		start = CharLineOffset(0, 0),
		end = offsetToCharLine(source, source.length),
	)
	editorState.removeStyleSpan(fullRange, style)
	for (span in spans) {
		if (span.range.start < 0 || span.range.endExclusive > source.length) continue
		val range = TextEditorRange(
			start = offsetToCharLine(source, span.range.start),
			end = offsetToCharLine(source, span.range.endExclusive),
		)
		editorState.addStyleSpan(range, style)
	}
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

/** Tracks which offset we last commanded on each pane so its echo emission isn't propagated back. */
private class ScrollSyncGuard {
	var suppressLeft: CharLineOffset? = null
	var suppressRight: CharLineOffset? = null
}

/**
 * Keep two editor panes scrolled to matching prose using the diff anchors.
 *
 * When one pane scrolls, its top-visible offset is mapped through [OffsetMap] to the other pane's
 * corresponding offset, and that pane is scrolled to align. A per-side guard swallows the single
 * echo emission produced by the programmatic follow-scroll so the two don't feed back into a loop.
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

	LaunchedEffect(leftState, rightState, offsetMap) {
		snapshotFlow { leftState.scrollManager.firstVisibleOffset }
			.collect { leftOffset ->
				if (guard.suppressLeft == leftOffset) {
					guard.suppressLeft = null
					return@collect
				}
				val rightAbs = offsetMap.leftToRight(leftState.absoluteOffsetOf(leftOffset))
				val rightOffset = rightState.charLineOffsetOf(rightAbs)
				guard.suppressRight = rightOffset
				rightState.scrollManager.scrollToPosition(rightOffset, top = true, animated = false)
			}
	}

	LaunchedEffect(leftState, rightState, offsetMap) {
		snapshotFlow { rightState.scrollManager.firstVisibleOffset }
			.collect { rightOffset ->
				if (guard.suppressRight == rightOffset) {
					guard.suppressRight = null
					return@collect
				}
				val leftAbs = offsetMap.rightToLeft(rightState.absoluteOffsetOf(rightOffset))
				val leftOffset = leftState.charLineOffsetOf(leftAbs)
				guard.suppressLeft = leftOffset
				leftState.scrollManager.scrollToPosition(leftOffset, top = true, animated = false)
			}
	}
}

/** Absolute char offset (into the editor's text) of a (line, char) position. */
private fun TextEditorState.absoluteOffsetOf(offset: CharLineOffset): Int {
	val lines = textLines
	var sum = 0
	var line = 0
	while (line < offset.line && line < lines.size) {
		sum += lines[line].length + 1 // + newline
		line++
	}
	return sum + offset.char
}

/** Inverse of [absoluteOffsetOf]: the (line, char) position for an absolute char offset. */
private fun TextEditorState.charLineOffsetOf(absolute: Int): CharLineOffset {
	val lines = textLines
	if (lines.isEmpty()) return CharLineOffset(0, 0)
	var remaining = absolute.coerceAtLeast(0)
	var line = 0
	while (line < lines.size) {
		val len = lines[line].length
		if (remaining <= len) return CharLineOffset(line, remaining)
		remaining -= (len + 1)
		line++
	}
	val last = lines.lastIndex
	return CharLineOffset(last, lines[last].length)
}
