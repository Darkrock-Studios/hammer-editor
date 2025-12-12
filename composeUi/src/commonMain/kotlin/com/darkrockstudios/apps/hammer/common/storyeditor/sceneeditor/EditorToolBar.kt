package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import com.darkrockstudios.apps.hammer.common.compose.icons.*
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.getSpanStylesInRange

@Composable
fun EditorToolBar(
	markdownState: MarkdownExtension,
	decreaseTextSize: () -> Unit,
	increaseTextSize: () -> Unit,
	resetTextSize: () -> Unit,
) {
	var isBoldActive by remember { mutableStateOf(false) }
	var isItalicActive by remember { mutableStateOf(false) }

	val state = remember(markdownState) { markdownState.editorState }

	LaunchedEffect(Unit) {
		state.cursorDataFlow.collect { (_, cursorStyles, selection) ->
			val styles = if (selection != null) {
				state.getSpanStylesInRange(selection)
			} else {
				cursorStyles
			}

			isBoldActive = styles.contains(markdownState.markdownStyles.BOLD)
			isItalicActive = styles.contains(markdownState.markdownStyles.ITALICS)
		}
	}

	Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
		EditorAction(
			icon = EditorIcons.IconBold,
			active = isBoldActive,
		) {
			toggleStyle(state, isBoldActive, markdownState.markdownStyles.BOLD)
		}
		EditorAction(
			icon = EditorIcons.IconItalic,
			active = isItalicActive,
		) {
			toggleStyle(state, isItalicActive, markdownState.markdownStyles.ITALICS)
		}

		Spacer(modifier = Modifier.weight(1f))

		EditorAction(
			icon = EditorIcons.IconUndo,
			active = state.canUndo
		) {
			state.undo()
		}
		EditorAction(
			icon = EditorIcons.IconRedo,
			active = state.canRedo
		) {
			state.redo()
		}

		EditorAction(
			icon = EditorIcons.IconTextDecrease,
			active = false,
		) {
			decreaseTextSize()
		}
		EditorAction(
			icon = EditorIcons.IconTextIncrease,
			active = false,
		) {
			increaseTextSize()
		}
		EditorAction(
			icon = EditorIcons.IconTextReset,
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
