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
		if (pane == PANE_DRAFT) {
			DraftPane(
				modifier = Modifier.fillMaxSize(),
				sectionNumber = 1,
				component = component,
			)
		} else {
			CurrentPane(
				modifier = Modifier.fillMaxSize(),
				sectionNumber = 1,
				component = component,
			)
		}
	}
}

@Composable
private fun ExpandedDraftCompareUi(modifier: Modifier, component: DraftCompare) {
	Row(modifier = modifier) {
		DraftPane(
			modifier = Modifier.weight(1f).fillMaxHeight(),
			sectionNumber = 1,
			component = component,
		)
		VerticalDivider(
			color = MaterialTheme.colorScheme.outlineVariant,
			thickness = Dp.Hairline,
		)
		CurrentPane(
			modifier = Modifier.weight(1f).fillMaxHeight(),
			sectionNumber = 2,
			component = component,
		)
	}
}

@Composable
private fun DraftPane(
	modifier: Modifier,
	sectionNumber: Int,
	component: DraftCompare,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	val markdownConfig = LocalMarkdownConfig.current
	val deletedStyle = rememberDeletedStyle()

	key(state.draftContent) {
		val textEditorState = rememberTextEditorState(
			initialText = getInitialEditorContent(state.draftContent, markdownConfig)
		)

		val draftSource = state.draftContent?.markdown
		LaunchedEffect(state.diffResult, state.showDiff, draftSource) {
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
}

@Composable
private fun CurrentPane(
	modifier: Modifier,
	sectionNumber: Int,
	component: DraftCompare,
) {
	val state by component.state.subscribeAsState()
	val markdownConfig = LocalMarkdownConfig.current
	val insertedStyle = rememberInsertedStyle()

	key(state.sceneContent) {
		val textEditorState = rememberTextEditorState(
			initialText = getInitialEditorContent(state.sceneContent, markdownConfig)
		)

		val markdownExtension = remember { textEditorState.withMarkdown(markdownConfig) }

		LaunchedEffect(markdownConfig) {
			markdownExtension.updateMarkdownConfiguration(markdownConfig)
		}

		LaunchedEffect(component.draftDef.draftName) {
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
		LaunchedEffect(state.diffResult, state.showDiff, initialSource) {
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
