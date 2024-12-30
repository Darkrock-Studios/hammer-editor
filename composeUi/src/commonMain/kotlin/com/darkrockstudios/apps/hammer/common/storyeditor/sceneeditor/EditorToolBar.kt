package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.texteditor.markdown.MarkdownStyles
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.getSpanStylesAtPosition
import com.darkrockstudios.texteditor.state.getSpanStylesInRange

val BOLD = MarkdownStyles.BOLD
val ITALICS = MarkdownStyles.ITALICS

@Composable
fun EditorToolBar(
	state: TextEditorState,
	decreaseTextSize: () -> Unit,
	increaseTextSize: () -> Unit,
	resetTextSize: () -> Unit,
) {
	var isBoldActive by remember { mutableStateOf(false) }
	var isItalicActive by remember { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		state.cursorPositionFlow.collect { position ->
			val selection = state.selector.selection
			val styles = if (selection != null) {
				state.getSpanStylesInRange(selection)
			} else {
				state.getSpanStylesAtPosition(position)
			}

			isBoldActive = styles.contains(BOLD)
			isItalicActive = styles.contains(ITALICS)
		}
	}

	Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
		EditorAction(
			iconRes = MR.images.icon_bold,
			active = isBoldActive,
		) {
			state.selector.selection?.let { range ->
				if (isBoldActive) {
					state.removeStyleSpan(range, BOLD)
				} else {
					state.addStyleSpan(range, BOLD)
				}
				isBoldActive = !isBoldActive
			}
		}
		EditorAction(
			iconRes = MR.images.icon_italic,
			active = isItalicActive,
		) {
			state.selector.selection?.let { range ->
				if (isItalicActive) {
					state.removeStyleSpan(range, ITALICS)
				} else {
					state.addStyleSpan(range, ITALICS)
				}
			}
			isItalicActive = !isItalicActive
		}
		EditorAction(
			iconRes = MR.images.icon_undo,
			active = state.canUndo
		) {
			state.undo()
		}
		EditorAction(
			iconRes = MR.images.icon_redo,
			active = state.canRedo
		) {
			state.redo()
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