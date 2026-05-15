package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.icons.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.markdown_format_bar_decrease_text_size
import com.darkrockstudios.apps.hammer.markdown_format_bar_increase_text_size
import com.darkrockstudios.apps.hammer.markdown_format_bar_reset_text_size
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.richstyle.OrderedListSpanStyle
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.getRichSpansAtPosition
import com.darkrockstudios.texteditor.state.getRichSpansInRange
import com.darkrockstudios.texteditor.state.getSpanStylesInRange

@Composable
fun MarkdownFormatBar(
	markdownState: MarkdownExtension,
	modifier: Modifier = Modifier.fillMaxWidth(),
	decreaseTextSize: (() -> Unit)? = null,
	increaseTextSize: (() -> Unit)? = null,
	resetTextSize: (() -> Unit)? = null,
) {
	var isBoldActive by remember { mutableStateOf(false) }
	var isItalicActive by remember { mutableStateOf(false) }
	var isStrikethroughActive by remember { mutableStateOf(false) }
	var isBlockquoteActive by remember { mutableStateOf(false) }
	var isOrderedListActive by remember { mutableStateOf(false) }
	var currentHeaderLevel by remember { mutableStateOf(0) }

	val state = remember(markdownState) { markdownState.editorState }

	LaunchedEffect(Unit) {
		state.cursorDataFlow.collect { (position, cursorStyles, selection) ->
			val styles = if (selection != null) {
				state.getSpanStylesInRange(selection)
			} else {
				cursorStyles
			}
			val richSpans = if (selection != null) {
				state.getRichSpansInRange(selection)
			} else {
				state.getRichSpansAtPosition(position)
			}

			isBoldActive = styles.contains(markdownState.markdownStyles.BOLD)
			isItalicActive = styles.contains(markdownState.markdownStyles.ITALICS)
			isStrikethroughActive = styles.contains(markdownState.markdownStyles.STRIKETHROUGH)
			isBlockquoteActive = styles.contains(markdownState.markdownStyles.BLOCKQUOTE)
			isOrderedListActive = richSpans.any { it.style === OrderedListSpanStyle }
			currentHeaderLevel = HEADER_CYCLE_LEVELS.firstOrNull { lvl ->
				styles.contains(markdownState.markdownStyles.header(lvl))
			} ?: 0
		}
	}

	LaunchedEffect(Unit) {
		state.editOperations.collect { reconcileHorizontalRules(state) }
	}

	val showOverflow = decreaseTextSize != null || increaseTextSize != null || resetTextSize != null

	BoxWithConstraints(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
		val compact = maxWidth < TOOLBAR_COMPACT_THRESHOLD
		val rowModifier = if (compact) {
			Modifier.horizontalScroll(rememberScrollState())
		} else {
			Modifier.fillMaxWidth()
		}
		Row(modifier = rowModifier) {
			FormatButtons(
				state = state,
				markdownState = markdownState,
				isBoldActive = isBoldActive,
				isItalicActive = isItalicActive,
				isStrikethroughActive = isStrikethroughActive,
				isBlockquoteActive = isBlockquoteActive,
				isOrderedListActive = isOrderedListActive,
				currentHeaderLevel = currentHeaderLevel,
			)

			if (compact) {
				Spacer(modifier = Modifier.width(8.dp))
			} else {
				Spacer(modifier = Modifier.weight(1f))
			}

			HistoryAndOverflow(
				state = state,
				decreaseTextSize = decreaseTextSize,
				increaseTextSize = increaseTextSize,
				resetTextSize = resetTextSize,
				showOverflow = showOverflow,
			)
		}
	}
}

private val TOOLBAR_COMPACT_THRESHOLD = 520.dp

@Composable
private fun RowScope.FormatButtons(
	state: TextEditorState,
	markdownState: MarkdownExtension,
	isBoldActive: Boolean,
	isItalicActive: Boolean,
	isStrikethroughActive: Boolean,
	isBlockquoteActive: Boolean,
	isOrderedListActive: Boolean,
	currentHeaderLevel: Int,
) {
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
	EditorAction(
		icon = EditorIcons.IconStrikethrough,
		active = isStrikethroughActive,
	) {
		toggleStyle(state, isStrikethroughActive, markdownState.markdownStyles.STRIKETHROUGH)
	}
	EditorTextAction(
		label = if (currentHeaderLevel == 0) "H" else "H$currentHeaderLevel",
		active = currentHeaderLevel != 0,
	) {
		cycleHeader(state, markdownState, currentHeaderLevel)
	}
	EditorAction(
		icon = Icons.Default.FormatQuote,
		active = isBlockquoteActive,
	) {
		toggleStyle(state, isBlockquoteActive, markdownState.markdownStyles.BLOCKQUOTE)
	}
	EditorAction(
		icon = Icons.AutoMirrored.Filled.FormatListBulleted,
		active = false,
	) {
		insertLineBullet(state)
	}
	EditorAction(
		icon = Icons.Default.FormatListNumbered,
		active = isOrderedListActive,
	) {
		toggleOrderedList(state, markdownState)
	}
	EditorAction(
		icon = Icons.Default.HorizontalRule,
		active = false,
	) {
		insertHorizontalRule(state)
	}
}

@Composable
private fun HistoryAndOverflow(
	state: TextEditorState,
	decreaseTextSize: (() -> Unit)?,
	increaseTextSize: (() -> Unit)?,
	resetTextSize: (() -> Unit)?,
	showOverflow: Boolean,
) {
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

	if (!showOverflow) return

	var menuExpanded by remember { mutableStateOf(false) }
	Box {
		EditorAction(
			icon = Icons.Default.MoreVert,
			active = false,
		) {
			menuExpanded = true
		}
		DropdownMenu(
			expanded = menuExpanded,
			onDismissRequest = { menuExpanded = false },
		) {
			if (decreaseTextSize != null) {
				DropdownMenuItem(
					text = { Text(Res.string.markdown_format_bar_decrease_text_size.get()) },
					leadingIcon = {
						Icon(
							imageVector = EditorIcons.IconTextDecrease,
							contentDescription = null,
						)
					},
					onClick = {
						decreaseTextSize()
						menuExpanded = false
					},
				)
			}
			if (increaseTextSize != null) {
				DropdownMenuItem(
					text = { Text(Res.string.markdown_format_bar_increase_text_size.get()) },
					leadingIcon = {
						Icon(
							imageVector = EditorIcons.IconTextIncrease,
							contentDescription = null,
						)
					},
					onClick = {
						increaseTextSize()
						menuExpanded = false
					},
				)
			}
			if (resetTextSize != null) {
				DropdownMenuItem(
					text = { Text(Res.string.markdown_format_bar_reset_text_size.get()) },
					leadingIcon = {
						Icon(
							imageVector = EditorIcons.IconTextReset,
							contentDescription = null,
						)
					},
					onClick = {
						resetTextSize()
						menuExpanded = false
					},
				)
			}
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
