package com.darkrockstudios.apps.hammer.common.storyeditor.focusmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusMode
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.EditorToolBar
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.getInitialEditorContent
import com.darkrockstudios.texteditor.TextEditor
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.rememberTextEditorState

@Composable
fun FocusModeUi(component: FocusMode) {
	val state by component.state.subscribeAsState()
	val lastForceUpdate by component.lastForceUpdate.subscribeAsState()

	val textEditorState = rememberTextEditorState(
		initialText = getInitialEditorContent(state.sceneBuffer?.content)
	)
	// TODO got to drive this from dark/light mode
	val markdownScheme by remember { mutableStateOf(MarkdownConfiguration.DEFAULT) }
	val markdownExtension =
		remember(state, markdownScheme) { textEditorState.withMarkdown(markdownScheme) }

	LaunchedEffect(Unit) {
		textEditorState.editOperations.collect { operation ->
			component.onContentChanged(ComposeRichText(markdownExtension))
		}
	}

	Column(modifier = Modifier.fillMaxSize()) {
		Row(
			modifier = Modifier
				.padding(
					start = Ui.Padding.L,
					end = Ui.Padding.L,
				)
				.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(
				state.sceneItem.name,
				style = MaterialTheme.typography.headlineLarge,
				color = MaterialTheme.colorScheme.onBackground,
			)

			IconButton(onClick = component::dismiss) {
				Icon(
					imageVector = Icons.Default.Close,
					contentDescription = MR.strings.scene_editor_menu_item_close.get(),
					tint = MaterialTheme.colorScheme.onBackground
				)
			}
		}

		EditorToolBar(
			markdownState = markdownExtension,
			decreaseTextSize = component::decreaseTextSize,
			increaseTextSize = component::increaseTextSize,
			resetTextSize = component::resetTextSize,
		)

		Row(
			modifier = Modifier.fillMaxSize(),
			horizontalArrangement = Arrangement.Center
		) {
			TextEditor(
				state = textEditorState,
				modifier = Modifier
					.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
					.fillMaxHeight()
					.widthIn(128.dp, TextEditorDefaults.MAX_WIDTH)
					.padding(horizontal = Ui.Padding.XL),
			)
		}
	}
}