package com.darkrockstudios.apps.hammer.common.storyeditor.drafts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftCompare
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.text.markdownToSnapshot
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.getInitialEditorContent
import com.darkrockstudios.richtexteditor.model.RichTextValue
import com.darkrockstudios.richtexteditor.ui.RichTextEditor
import com.darkrockstudios.richtexteditor.ui.defaultRichTextFieldStyle

@Composable
fun DraftCompareUi(component: DraftCompare) {
	val screen = LocalScreenCharacteristic.current

	Column(modifier = Modifier.fillMaxSize()) {
		if (LocalScreenCharacteristic.current.needsExplicitClose) {
			IconButton(
				onClick = { component.cancel() },
				modifier = Modifier.align(Alignment.End)
			) {
				Icon(
					Icons.Default.Cancel,
					contentDescription = MR.strings.draft_compare_cancel_button.get(),
					tint = MaterialTheme.colorScheme.onBackground
				)
			}
		}

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
	var tabState by rememberSaveable { mutableStateOf(0) }
	val titles = remember {
		listOf(MR.strings.draft_compare_tab_title_draft, MR.strings.draft_compare_tab_title_current)
	}

	Column(modifier = modifier) {
		TabRow(selectedTabIndex = tabState) {
			titles.forEachIndexed { index, title ->
				Tab(
					text = { Text(title.get()) },
					selected = tabState == index,
					onClick = { tabState = index }
				)
			}
		}
		if (tabState == 0) {
			DraftContent(
				modifier = Modifier.weight(1f),
				component = component
			)
		} else if (tabState == 1) {
			CurrentContent(
				modifier = Modifier.weight(1f),
				component = component
			)
		}
	}
}

@Composable
private fun ExpandedDraftCompareUi(modifier: Modifier, component: DraftCompare) {
	Row(modifier = modifier) {
		DraftContent(
			modifier = Modifier.weight(1f),
			component = component,
		)

		CurrentContent(
			modifier = Modifier.weight(1f),
			component = component
		)
	}
}

@Composable
private fun CurrentContent(
	modifier: Modifier,
	component: DraftCompare
) {
	val state by component.state.subscribeAsState()

	// I feel like there must be a better way...
	var sceneText by remember(state.sceneContent) {
		val existing = state.mergedContent as? ComposeRichText

		if (existing == null && state.sceneContent != null) {
			val sceneSnapshot = (state.sceneContent?.markdown?.markdownToSnapshot())
			if (sceneSnapshot != null) {
				component.onMergedContentChanged(ComposeRichText(sceneSnapshot))
			}

			mutableStateOf(
				RichTextValue.fromSnapshot(
					sceneSnapshot ?: "".markdownToSnapshot()
				)
			)
		} else {
			mutableStateOf(
				RichTextValue.fromSnapshot(
					existing?.snapshot ?: "".markdownToSnapshot()
				)
			)
		}
	}

	Card(
		modifier = modifier.padding(Ui.Padding.L),
		border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiaryContainer),
		elevation = CardDefaults.outlinedCardElevation(
			defaultElevation = Ui.Elevation.MEDIUM
		),
	) {
		Column(modifier = Modifier.padding(Ui.Padding.L)) {
			Text(
				MR.strings.draft_compare_current_header.get(),
				style = MaterialTheme.typography.headlineLarge
			)
			Text(
				MR.strings.draft_compare_current_subheader.get(),
				style = MaterialTheme.typography.bodySmall,
				fontStyle = FontStyle.Italic
			)

			Button(onClick = { component.pickMerged() }) {
				Text(MR.strings.draft_compare_current_accept_button.get())
			}

			RichTextEditor(
				modifier = Modifier.fillMaxSize(),
				value = sceneText,
				onValueChange = { rtv ->
					component.onMergedContentChanged(ComposeRichText(rtv.getLastSnapshot()))
					sceneText = rtv
				},
				textFieldStyle = defaultRichTextFieldStyle().copy(
					placeholder = MR.strings.draft_compare_current_body_placeholder.get(),
					textColor = MaterialTheme.colorScheme.onBackground,
					placeholderColor = MaterialTheme.colorScheme.onBackground,
				)
			)
		}
	}
}

@Composable
private fun DraftContent(
	modifier: Modifier,
	component: DraftCompare,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	var draftText by remember(state.draftContent) { mutableStateOf(getInitialEditorContent(state.draftContent)) }

	Card(modifier = modifier.padding(Ui.Padding.L)) {
		Column(modifier = Modifier.padding(Ui.Padding.L)) {

			/*
			val date = remember(component.draftDef.draftTimestamp) {
				val created = component.draftDef.draftTimestamp.toLocalDateTime(TimeZone.currentSystemDefault())
				created.format("dd MMM `yy")
			}
			*/

			Text(
				strRes.get(MR.strings.draft_compare_draft_header, component.draftDef.draftName),
				style = MaterialTheme.typography.headlineLarge
			)
			Text(
				MR.strings.draft_compare_draft_subheader.get(),
				style = MaterialTheme.typography.bodySmall,
				fontStyle = FontStyle.Italic
			)
			Button(onClick = { component.pickDraft() }) {
				Text(MR.strings.draft_compare_draft_accept_button.get())
			}

			RichTextEditor(
				modifier = Modifier.fillMaxSize(),
				value = draftText,
				onValueChange = { rtv ->
					draftText = rtv
				},
				textFieldStyle = defaultRichTextFieldStyle().copy(
					placeholder = MR.strings.draft_compare_draft_body_placeholder.get(),
					textColor = MaterialTheme.colorScheme.onBackground,
					placeholderColor = MaterialTheme.colorScheme.onBackground,
				),
				readOnly = true
			)
		}
	}
}