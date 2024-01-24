package com.darkrockstudios.apps.hammer.common.storyeditor.focusmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusMode
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.EditorToolBar
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.getInitialEditorContent
import com.darkrockstudios.richtexteditor.ui.RichTextEditor
import com.darkrockstudios.richtexteditor.ui.defaultRichTextFieldStyle

@Composable
fun FocusModeUi(component: FocusMode) {
	val state by component.state.subscribeAsState()
	val lastForceUpdate by component.lastForceUpdate.subscribeAsState()

	var sceneText by remember {
		mutableStateOf(
			getInitialEditorContent(state.sceneBuffer?.content)
		)
	}

	LaunchedEffect(lastForceUpdate) {
		sceneText = getInitialEditorContent(state.sceneBuffer?.content)
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
			sceneText = sceneText,
			setSceneText = { sceneText = it },
			decreaseTextSize = component::decreaseTextSize,
			increaseTextSize = component::increaseTextSize,
			resetTextSize = component::resetTextSize,
		)

		Row(
			modifier = Modifier.fillMaxSize(),
			horizontalArrangement = Arrangement.Center
		) {
			RichTextEditor(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
					.fillMaxHeight()
					.widthIn(128.dp, TextEditorDefaults.MAX_WIDTH)
					.padding(horizontal = Ui.Padding.XL),
				value = sceneText,
				onValueChange = { rtv ->
					sceneText = rtv
					component.onContentChanged(ComposeRichText(rtv.getLastSnapshot()))
				},
				textFieldStyle = defaultRichTextFieldStyle().copy(
					placeholder = MR.strings.scene_editor_body_placeholder.get(),
					textColor = MaterialTheme.colorScheme.onSurface,
					placeholderColor = MaterialTheme.colorScheme.onSurface,
					textStyle = MaterialTheme.typography.bodyLarge.copy(
						fontSize = state.textSize.sp
					),
				),
			)
		}
	}
}