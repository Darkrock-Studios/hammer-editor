package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.texteditor.markdown.MarkdownStyles
import com.darkrockstudios.texteditor.state.TextEditorState
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
		state.cursorDataFlow.collect { (_, cursorStyles, selection) ->
			val styles = if (selection != null) {
				state.getSpanStylesInRange(selection)
			} else {
				cursorStyles
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
			toggleStyle(state, isBoldActive, BOLD)
		}
		EditorAction(
			iconRes = MR.images.icon_italic,
			active = isItalicActive,
		) {
			toggleStyle(state, isItalicActive, ITALICS)
		}

		Spacer(modifier = Modifier.weight(1f))

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

private fun toggleStyle(
	state: TextEditorState,
	isActive: Boolean,
	spanStyle: SpanStyle
) {
	val selection = state.selector.selection
	if (selection != null) {
		if (isActive) {
			state.removeStyleSpan(selection, spanStyle)
		} else {
			state.addStyleSpan(selection, spanStyle)
		}
	} else {
		if (isActive) {
			state.cursor.removeStyle(spanStyle)
		} else {
			state.cursor.addStyle(spanStyle)
		}
	}
}