package com.darkrockstudios.apps.hammer.common.storyeditor.drafts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftCompare
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.getInitialEditorContent
import com.darkrockstudios.texteditor.TextEditor
import com.darkrockstudios.texteditor.state.rememberTextEditorState

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

	val textEditorState = rememberTextEditorState(
		initialText = getInitialEditorContent(state.sceneContent)
	)
	// I feel like there must be a better way...
//	var sceneText by remember(state.sceneContent) {
//		val existing = state.mergedContent as? ComposeRichText
//
//		if (existing == null && state.sceneContent != null) {
//			val sceneSnapshot = (state.sceneContent?.markdown?.toAnnotatedStringFromMarkdown())
//			if (sceneSnapshot != null) {
//				component.onMergedContentChanged(ComposeRichText(snapshot = sceneSnapshot))
//			}
//
//			mutableStateOf(
//				sceneSnapshot ?: AnnotatedString("")
//			)
//		} else {
//			mutableStateOf(
//				RichTextValue.fromSnapshot(
//					existing?.snapshot ?: "".markdownToSnapshot()
//				)
//			)
//		}
//	}

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

			TextEditor(
				modifier = Modifier.fillMaxSize(),
				state = textEditorState,
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

	val textEditorState = rememberTextEditorState(
		initialText = getInitialEditorContent(state.draftContent)
	)

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

			TextEditor(
				modifier = Modifier.fillMaxSize(),
				state = textEditorState,
				enabled = true
			)
		}
	}
}