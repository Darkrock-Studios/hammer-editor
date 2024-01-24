package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.richtexteditor.model.RichTextValue
import com.darkrockstudios.richtexteditor.model.Style

@Composable
fun EditorToolBar(
	sceneText: RichTextValue,
	setSceneText: (RichTextValue) -> Unit,
	decreaseTextSize: () -> Unit,
	increaseTextSize: () -> Unit,
	resetTextSize: () -> Unit,
) {
	Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
		EditorAction(
			iconRes = MR.images.icon_bold,
			active = sceneText.currentStyles.contains(Style.Bold),
		) {
			setSceneText(sceneText.insertStyle(Style.Bold))
		}
		EditorAction(
			iconRes = MR.images.icon_italic,
			active = sceneText.currentStyles.contains(Style.Italic),
		) {
			setSceneText(sceneText.insertStyle(Style.Italic))
		}
		EditorAction(
			iconRes = MR.images.icon_undo,
			active = sceneText.isUndoAvailable
		) {
			setSceneText(sceneText.undo())
		}
		EditorAction(
			iconRes = MR.images.icon_redo,
			active = sceneText.isRedoAvailable
		) {
			setSceneText(sceneText.redo())
		}

		EditorAction(
			iconRes = MR.images.icon_text_decrease,
			active = false,
		) {
			decreaseTextSize()
		}
		EditorAction(
			iconRes = MR.images.icon_text_increase,
			active = false,
		) {
			increaseTextSize()
		}
		EditorAction(
			iconRes = MR.images.icon_text_reset,
			active = false,
		) {
			resetTextSize()
		}
	}

}