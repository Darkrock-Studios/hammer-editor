package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.LocalEditorTextStyle
import com.darkrockstudios.apps.hammer.common.compose.boldShortcutModifier
import com.darkrockstudios.apps.hammer.common.compose.icons.EditorIcons
import com.darkrockstudios.apps.hammer.common.compose.icons.IconBold
import com.darkrockstudios.apps.hammer.common.compose.icons.IconItalic
import com.darkrockstudios.apps.hammer.common.compose.icons.IconRedo
import com.darkrockstudios.apps.hammer.common.compose.icons.IconStrikethrough
import com.darkrockstudios.apps.hammer.common.compose.icons.IconTextDecrease
import com.darkrockstudios.apps.hammer.common.compose.icons.IconTextIncrease
import com.darkrockstudios.apps.hammer.common.compose.icons.IconTextReset
import com.darkrockstudios.apps.hammer.common.compose.icons.IconUndo
import com.darkrockstudios.apps.hammer.common.compose.italicShortcutModifier
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.strikethroughShortcutModifier
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.markdown_format_bar_blockquote
import com.darkrockstudios.apps.hammer.markdown_format_bar_bold
import com.darkrockstudios.apps.hammer.markdown_format_bar_bullet_list
import com.darkrockstudios.apps.hammer.markdown_format_bar_decrease_text_size
import com.darkrockstudios.apps.hammer.markdown_format_bar_find_replace
import com.darkrockstudios.apps.hammer.markdown_format_bar_heading
import com.darkrockstudios.apps.hammer.markdown_format_bar_horizontal_rule
import com.darkrockstudios.apps.hammer.markdown_format_bar_increase_text_size
import com.darkrockstudios.apps.hammer.markdown_format_bar_italic
import com.darkrockstudios.apps.hammer.markdown_format_bar_numbered_list
import com.darkrockstudios.apps.hammer.markdown_format_bar_redo
import com.darkrockstudios.apps.hammer.markdown_format_bar_reset_text_size
import com.darkrockstudios.apps.hammer.markdown_format_bar_text_size
import com.darkrockstudios.apps.hammer.markdown_format_bar_strikethrough
import com.darkrockstudios.apps.hammer.markdown_format_bar_undo
import com.darkrockstudios.apps.hammer.more_menu_button
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.richstyle.BulletListSpanStyle
import com.darkrockstudios.texteditor.richstyle.OrderedListSpanStyle
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.getRichSpansAtPosition
import com.darkrockstudios.texteditor.state.getRichSpansInRange
import com.darkrockstudios.texteditor.state.getSpanStylesInRange
import com.darkrockstudios.texteditor.state.headerLevel

@Composable
fun MarkdownFormatBar(
	markdownState: MarkdownExtension,
	modifier: Modifier = Modifier.fillMaxWidth(),
	decreaseTextSize: (() -> Unit)? = null,
	increaseTextSize: (() -> Unit)? = null,
	resetTextSize: (() -> Unit)? = null,
	onFindReplace: (() -> Unit)? = null,
) {
	var isBoldActive by remember { mutableStateOf(false) }
	var isItalicActive by remember { mutableStateOf(false) }
	var isStrikethroughActive by remember { mutableStateOf(false) }
	var isBlockquoteActive by remember { mutableStateOf(false) }
	var isBulletListActive by remember { mutableStateOf(false) }
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

			isBoldActive = styles.contains(state.richTextStyles.boldStyle)
			isItalicActive = styles.contains(state.richTextStyles.italicStyle)
			isStrikethroughActive = styles.contains(state.richTextStyles.strikethroughStyle)
			isBlockquoteActive = styles.contains(state.richTextStyles.blockquoteStyle)
			isBulletListActive = richSpans.any { it.style === BulletListSpanStyle }
			isOrderedListActive = richSpans.any { it.style === OrderedListSpanStyle }
			currentHeaderLevel = state.headerLevel(selection?.start?.line ?: position.line) ?: 0
		}
	}

	LaunchedEffect(Unit) {
		state.editOperations.collect { reconcileHorizontalRules(state) }
	}

	val showOverflow = decreaseTextSize != null || increaseTextSize != null ||
		resetTextSize != null || onFindReplace != null

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
				isBoldActive = isBoldActive,
				isItalicActive = isItalicActive,
				isStrikethroughActive = isStrikethroughActive,
				isBlockquoteActive = isBlockquoteActive,
				isBulletListActive = isBulletListActive,
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
				onFindReplace = onFindReplace,
				showOverflow = showOverflow,
			)
		}
	}
}

private val TOOLBAR_COMPACT_THRESHOLD = 520.dp

@Composable
private fun RowScope.FormatButtons(
	state: TextEditorState,
	isBoldActive: Boolean,
	isItalicActive: Boolean,
	isStrikethroughActive: Boolean,
	isBlockquoteActive: Boolean,
	isBulletListActive: Boolean,
	isOrderedListActive: Boolean,
	currentHeaderLevel: Int,
) {
	EditorTooltip("${Res.string.markdown_format_bar_bold.get()} (${shortcutHint("B")})") {
		EditorAction(
			icon = EditorIcons.IconBold,
			active = isBoldActive,
		) {
			toggleStyle(state, state.richTextStyles.boldStyle)
		}
	}
	EditorTooltip("${Res.string.markdown_format_bar_italic.get()} (${shortcutHint("I")})") {
		EditorAction(
			icon = EditorIcons.IconItalic,
			active = isItalicActive,
		) {
			toggleStyle(state, state.richTextStyles.italicStyle)
		}
	}
	EditorTooltip(
		"${Res.string.markdown_format_bar_strikethrough.get()} (${
			shortcutHint(
				"X",
				shift = true
			)
		})"
	) {
		EditorAction(
			icon = EditorIcons.IconStrikethrough,
			active = isStrikethroughActive,
		) {
			toggleStyle(state, state.richTextStyles.strikethroughStyle)
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_heading.get()) {
		EditorTextAction(
			label = if (currentHeaderLevel == 0) "H" else "H$currentHeaderLevel",
			active = currentHeaderLevel != 0,
		) {
			cycleHeader(state, currentHeaderLevel)
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_blockquote.get()) {
		EditorAction(
			icon = Icons.Default.FormatQuote,
			active = isBlockquoteActive,
		) {
			toggleStyle(state, state.richTextStyles.blockquoteStyle)
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_bullet_list.get()) {
		EditorAction(
			icon = Icons.AutoMirrored.Filled.FormatListBulleted,
			active = isBulletListActive,
		) {
			toggleBulletList(state)
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_numbered_list.get()) {
		EditorAction(
			icon = Icons.Default.FormatListNumbered,
			active = isOrderedListActive,
		) {
			toggleOrderedList(state)
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_horizontal_rule.get()) {
		EditorAction(
			icon = Icons.Default.HorizontalRule,
			active = false,
		) {
			insertHorizontalRule(state)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTooltip(
	text: String,
	content: @Composable () -> Unit,
) {
	TooltipBox(
		positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
		tooltip = { PlainTooltip { Text(text) } },
		state = rememberTooltipState(),
	) {
		content()
	}
}

@Composable
private fun HistoryAndOverflow(
	state: TextEditorState,
	decreaseTextSize: (() -> Unit)?,
	increaseTextSize: (() -> Unit)?,
	resetTextSize: (() -> Unit)?,
	onFindReplace: (() -> Unit)?,
	showOverflow: Boolean,
) {
	EditorTooltip(Res.string.markdown_format_bar_undo.get()) {
		EditorAction(
			icon = EditorIcons.IconUndo,
			active = state.canUndo
		) {
			state.undo()
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_redo.get()) {
		EditorAction(
			icon = EditorIcons.IconRedo,
			active = state.canRedo
		) {
			state.redo()
		}
	}

	if (!showOverflow) return

	var menuExpanded by remember { mutableStateOf(false) }
	Box {
		EditorTooltip(Res.string.more_menu_button.get()) {
			EditorAction(
				icon = Icons.Default.MoreVert,
				active = false,
			) {
				menuExpanded = true
			}
		}
		DropdownMenu(
			expanded = menuExpanded,
			onDismissRequest = { menuExpanded = false },
		) {
			if (onFindReplace != null) {
				DropdownMenuItem(
					text = { Text(Res.string.markdown_format_bar_find_replace.get()) },
					leadingIcon = {
						Icon(
							imageVector = Icons.Default.Search,
							contentDescription = null,
						)
					},
					onClick = {
						onFindReplace()
						menuExpanded = false
					},
				)
			}
			if (decreaseTextSize != null || increaseTextSize != null || resetTextSize != null) {
				TextSizeMenuRow(decreaseTextSize, increaseTextSize, resetTextSize)
			}
		}
	}
}

/** Steps the body text size and shows it; the menu stays open while stepping. */
@Composable
private fun TextSizeMenuRow(
	decreaseTextSize: (() -> Unit)?,
	increaseTextSize: (() -> Unit)?,
	resetTextSize: (() -> Unit)?,
) {
	val textSize = LocalEditorTextStyle.current.fontSize.value
	Row(
		modifier = Modifier.padding(start = 12.dp, end = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = Res.string.markdown_format_bar_text_size.get(),
			style = MaterialTheme.typography.bodyLarge,
			modifier = Modifier.padding(end = 12.dp),
		)
		if (decreaseTextSize != null) {
			IconButton(onClick = decreaseTextSize) {
				Icon(
					imageVector = EditorIcons.IconTextDecrease,
					contentDescription = Res.string.markdown_format_bar_decrease_text_size.get(),
				)
			}
		}
		Text(
			text = textSize.toInt().toString(),
			style = MaterialTheme.typography.bodyLarge,
			textAlign = TextAlign.Center,
			modifier = Modifier.widthIn(min = 28.dp),
		)
		if (increaseTextSize != null) {
			IconButton(onClick = increaseTextSize) {
				Icon(
					imageVector = EditorIcons.IconTextIncrease,
					contentDescription = Res.string.markdown_format_bar_increase_text_size.get(),
				)
			}
		}
		if (resetTextSize != null) {
			IconButton(
				onClick = resetTextSize,
				enabled = textSize != GlobalSettings.DEFAULT_FONT_SIZE,
			) {
				Icon(
					imageVector = EditorIcons.IconTextReset,
					contentDescription = Res.string.markdown_format_bar_reset_text_size.get(),
				)
			}
		}
	}
}

/**
 * Hooks Ctrl/Cmd+B, Ctrl/Cmd+I and Ctrl/Cmd+Shift+X up to bold, italic and
 * strikethrough so the inline styles in the format bar are also reachable from
 * the keyboard. Apply to an ancestor of the editor (the events are caught in the
 * preview phase, before the editor's own key handling sees them).
 */
fun Modifier.markdownFormatShortcuts(markdownExtension: MarkdownExtension): Modifier {
	val state = markdownExtension.editorState
	return this
		.boldShortcutModifier { toggleStyle(state, state.richTextStyles.boldStyle) }
		.italicShortcutModifier { toggleStyle(state, state.richTextStyles.italicStyle) }
		.strikethroughShortcutModifier { toggleStyle(state, state.richTextStyles.strikethroughStyle) }
}

/**
 * Toggles [spanStyle] over the current selection, or at the cursor when there is
 * no selection. The active state is read synchronously from the editor so this is
 * safe to call from a keyboard shortcut as well as a toolbar button.
 */
private fun toggleStyle(
	state: TextEditorState,
	spanStyle: SpanStyle,
) {
	val selection = state.selector.selection
	if (selection != null) {
		val isActive = state.getSpanStylesInRange(selection).contains(spanStyle)
		if (isActive) {
			state.removeStyleSpan(selection, spanStyle)
		} else {
			state.addStyleSpan(selection, spanStyle)
		}
	} else {
		val isActive = state.cursor.styles.contains(spanStyle)
		if (isActive) {
			state.cursor.removeStyle(spanStyle)
		} else {
			state.cursor.addStyle(spanStyle)
		}
	}
}
