package com.darkrockstudios.apps.hammer.common.projecteditor.drafts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.getInitialEditorContent
import com.darkrockstudios.richtexteditor.ui.RichTextEditor
import com.darkrockstudios.richtexteditor.ui.defaultRichTextFieldStyle

@Composable
fun DraftCompareUi(component: DraftCompare) {
	LaunchedEffect(component.sceneItem) {
		component.loadContents()
	}

	Column(modifier = Modifier.fillMaxSize()) {
		if (LocalScreenCharacteristic.current.needsExplicitClose) {
			IconButton(
				onClick = { component.cancel() },
				modifier = Modifier.align(Alignment.End)
			) {
				Icon(
					Icons.Default.Cancel,
					contentDescription = "Cancel",
					tint = MaterialTheme.colorScheme.onBackground
				)
			}
		}

		if (LocalScreenCharacteristic.current.isWide) {
			Row(modifier = Modifier.fillMaxSize()) {
				DraftContent(
					modifier = Modifier.weight(1f),
					component = component,
				)

				CurrentContent(
					modifier = Modifier.weight(1f),
					component = component
				)
			}
		} else {
			Column(modifier = Modifier.fillMaxSize()) {
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
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrentContent(
	modifier: Modifier,
	component: DraftCompare
) {
	val state by component.state.subscribeAsState()
	var sceneText by remember(state.sceneContent) { mutableStateOf(getInitialEditorContent(state.sceneContent)) }

	Card(modifier = modifier.padding(Ui.Padding.L)) {
		Column(modifier = Modifier.padding(Ui.Padding.L)) {
			Text(
				"Merge with Current",
				style = MaterialTheme.typography.headlineLarge
			)
			Text(
				"Copy parts of the draft into here",
				style = MaterialTheme.typography.bodySmall,
				fontStyle = FontStyle.Italic
			)

			Button(onClick = { component.pickMerged() }) {
				Text("Take Merged")
			}

			RichTextEditor(
				modifier = Modifier.fillMaxSize(),
				value = sceneText,
				onValueChange = { rtv ->
					sceneText = rtv
					component.onContentChanged(ComposeRichText(rtv.getLastSnapshot()))
				},
				textFieldStyle = defaultRichTextFieldStyle().copy(
					placeholder = "Begin writing your Scene here",
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
				"Draft: ${component.draftDef.draftName}",
				style = MaterialTheme.typography.headlineLarge
			)
			Text(
				"No edits here will be saved",
				style = MaterialTheme.typography.bodySmall,
				fontStyle = FontStyle.Italic
			)
			Button(onClick = { component.pickDraft() }) {
				Text("Take this whole draft")
			}

			RichTextEditor(
				modifier = Modifier.fillMaxSize(),
				value = draftText,
				onValueChange = { rtv ->
					draftText = rtv
				},
				textFieldStyle = defaultRichTextFieldStyle().copy(
					placeholder = "Draft",
					textColor = MaterialTheme.colorScheme.onBackground,
					placeholderColor = MaterialTheme.colorScheme.onBackground,
				)
			)
		}
	}
}