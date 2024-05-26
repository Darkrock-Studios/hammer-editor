package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.Start
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import kotlinx.coroutines.launch

@Composable
actual fun EditorTopBar(
	component: SceneEditor,
	rootSnackbar: RootSnackbarHostState,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	Row(
		verticalAlignment = CenterVertically,
		horizontalArrangement = Start
	) {
		if (state.isEditingName) {
			var editSceneNameValue by remember { mutableStateOf(state.sceneItem.name) }

			TextField(
				value = editSceneNameValue,
				onValueChange = { editSceneNameValue = it },
				modifier = Modifier.padding(Ui.Padding.S),
				label = { Text(MR.strings.scene_editor_name_hint.get()) }
			)
			IconButton(onClick = { scope.launch { component.changeSceneName(editSceneNameValue) } }) {
				Icon(
					Icons.Filled.Check,
					MR.strings.scene_editor_rename_button.get(),
					tint = MaterialTheme.colorScheme.onSurface
				)
			}
			IconButton(onClick = component::endSceneNameEdit) {
				Icon(
					Icons.Filled.Cancel,
					MR.strings.scene_editor_cancel_button.get(),
					tint = MaterialTheme.colorScheme.error
				)
			}
		} else {
			ClickableText(
				AnnotatedString(state.sceneItem.name),
				modifier = Modifier.padding(
					horizontal = Ui.Padding.XL,
					vertical = Ui.Padding.S,
				)
					.weight(1f),
				onClick = { component.beginSceneNameEdit() },
				style = MaterialTheme.typography.headlineMedium
					.copy(
						color = MaterialTheme.colorScheme.onBackground,
						//textAlign = TextAlign.Center
					),
			)

			val unsaved = state.sceneBuffer?.dirty == true
			if (unsaved) {
				Row(
					modifier = Modifier.width(IntrinsicSize.Min),
					horizontalArrangement = Arrangement.End,
				) {
					Badge(
						modifier = Modifier.align(Alignment.Top).padding(top = Ui.Padding.L)
					) { Text(MR.strings.scene_editor_unsaved_chip.get()) }

					Spacer(modifier = Modifier.weight(1f))

					IconButton(onClick = {
						scope.launch {
							component.storeSceneContent()
							rootSnackbar.showSnackbar(strRes.get(MR.strings.scene_editor_toast_save_successful))
						}
					}) {
						Icon(
							Icons.Filled.Save,
							contentDescription = MR.strings.scene_editor_save_button.get(),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
				}
			}

			IconButton(onClick = component::toggleMetadataVisibility) {
				Icon(
					imageVector = Icons.Default.Info,
					contentDescription = MR.strings.scene_editor_metadata_hide_button .get(),
					tint = MaterialTheme.colorScheme.onBackground
				)
			}

			IconButton(onClick = component::enterFocusMode) {
				Icon(
					imageVector = Icons.Default.Fullscreen,
					contentDescription = MR.strings.scene_editor_focus_mode_button.get(),
					tint = MaterialTheme.colorScheme.onBackground
				)
			}

			IconButton(onClick = component::closeEditor) {
				Icon(
					imageVector = Icons.Default.Close,
					contentDescription = MR.strings.scene_editor_menu_item_close.get(),
					tint = MaterialTheme.colorScheme.onBackground
				)
			}
		}
	}
}
