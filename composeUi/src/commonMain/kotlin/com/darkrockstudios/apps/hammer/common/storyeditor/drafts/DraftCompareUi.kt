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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
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
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.getInitialEditorContent
import com.darkrockstudios.apps.hammer.draft_compare_current_accept_button
import com.darkrockstudios.apps.hammer.draft_compare_current_header
import com.darkrockstudios.apps.hammer.draft_compare_current_subheader
import com.darkrockstudios.apps.hammer.draft_compare_draft_accept_button
import com.darkrockstudios.apps.hammer.draft_compare_draft_header
import com.darkrockstudios.apps.hammer.draft_compare_draft_subheader
import com.darkrockstudios.apps.hammer.draft_compare_tab_title_current
import com.darkrockstudios.apps.hammer.draft_compare_tab_title_draft
import com.darkrockstudios.texteditor.TextEditor
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.rememberTextEditorState

private const val PANE_DRAFT = 0
private const val PANE_CURRENT = 1

@Composable
fun DraftCompareUi(component: DraftCompare) {
	val screen = LocalScreenCharacteristic.current
	val draftName = component.draftDef.draftName

	Column(modifier = Modifier.fillMaxSize()) {
		HdMasthead(
			section = "DRAFT COMPARE",
			leadingMeta = listOf(draftName),
			trailing = {
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

	key(state.draftContent) {
		val textEditorState = rememberTextEditorState(
			initialText = getInitialEditorContent(state.draftContent, markdownConfig)
		)

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

	key(state.sceneContent) {
		val textEditorState = rememberTextEditorState(
			initialText = getInitialEditorContent(state.sceneContent, markdownConfig)
		)

		val markdownExtension = remember { textEditorState.withMarkdown(markdownConfig) }

		LaunchedEffect(markdownConfig) {
			markdownExtension.updateMarkdownConfiguration(markdownConfig)
		}

		LaunchedEffect(component.draftDef.draftName) {
			textEditorState.editOperations.collect { _ ->
				component.onMergedContentChanged(ComposeRichText(markdownExtension))
			}
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
