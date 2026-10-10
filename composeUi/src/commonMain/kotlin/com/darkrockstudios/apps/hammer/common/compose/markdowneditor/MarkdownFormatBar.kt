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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.window.PopupProperties
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
import com.darkrockstudios.apps.hammer.markdown_format_bar_highlight
import com.darkrockstudios.apps.hammer.markdown_format_bar_horizontal_rule
import com.darkrockstudios.apps.hammer.markdown_format_bar_increase_text_size
import com.darkrockstudios.apps.hammer.markdown_format_bar_italic
import com.darkrockstudios.apps.hammer.markdown_format_bar_numbered_list
import com.darkrockstudios.apps.hammer.markdown_format_bar_redo
import com.darkrockstudios.apps.hammer.markdown_format_bar_reset_text_size
import com.darkrockstudios.apps.hammer.markdown_format_bar_text_size
import com.darkrockstudios.apps.hammer.markdown_format_bar_strikethrough
import com.darkrockstudios.apps.hammer.markdown_format_bar_table
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_align_center
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_align_left
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_align_right
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_column_left
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_column_right
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_delete
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_delete_column
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_delete_row
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_insert
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_row_above
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_row_below
import com.darkrockstudios.apps.hammer.markdown_format_bar_table_to_text
import com.darkrockstudios.apps.hammer.markdown_format_bar_task_list
import com.darkrockstudios.apps.hammer.markdown_format_bar_undo
import com.darkrockstudios.apps.hammer.more_menu_button
import com.darkrockstudios.texteditor.TextEditorRange
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.richstyle.BlockquoteSpanStyle
import com.darkrockstudios.texteditor.richstyle.BulletListSpanStyle
import com.darkrockstudios.texteditor.richstyle.OrderedListSpanStyle
import com.darkrockstudios.texteditor.richstyle.TableAlignment
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.convertTableToText
import com.darkrockstudios.texteditor.state.deleteTable
import com.darkrockstudios.texteditor.state.deleteTableColumn
import com.darkrockstudios.texteditor.state.deleteTableRow
import com.darkrockstudios.texteditor.state.getRichSpansAtPosition
import com.darkrockstudios.texteditor.state.getRichSpansInRange
import com.darkrockstudios.texteditor.state.hasStyleThroughout
import com.darkrockstudios.texteditor.state.headerLevel
import com.darkrockstudios.texteditor.state.insertTable
import com.darkrockstudios.texteditor.state.insertTableColumn
import com.darkrockstudios.texteditor.state.insertTableRow
import com.darkrockstudios.texteditor.state.isInlineOnlyLine
import com.darkrockstudios.texteditor.state.isTask
import com.darkrockstudios.texteditor.state.isTableCell
import com.darkrockstudios.texteditor.state.setTableColumnAlignment
import com.darkrockstudios.texteditor.state.tableCellAt
import com.darkrockstudios.texteditor.state.toggleSpanStyle

/**
 * The editor's format bar. [extended] adds highlight, task lists and tables, which the
 * exporters don't render as Hammer shows them: for text that is never exported (notes,
 * entries, ideas, events), not scenes.
 */
@Composable
fun MarkdownFormatBar(
	markdownState: MarkdownExtension,
	modifier: Modifier = Modifier.fillMaxWidth(),
	extended: Boolean = false,
	decreaseTextSize: (() -> Unit)? = null,
	increaseTextSize: (() -> Unit)? = null,
	resetTextSize: (() -> Unit)? = null,
	onFindReplace: (() -> Unit)? = null,
) {
	var isBoldActive by remember { mutableStateOf(false) }
	var isItalicActive by remember { mutableStateOf(false) }
	var isStrikethroughActive by remember { mutableStateOf(false) }
	var isHighlightActive by remember { mutableStateOf(false) }
	var isTaskActive by remember { mutableStateOf(false) }
	var isBlockquoteActive by remember { mutableStateOf(false) }
	var isBulletListActive by remember { mutableStateOf(false) }
	var isOrderedListActive by remember { mutableStateOf(false) }
	var currentHeaderLevel by remember { mutableStateOf(0) }
	var tableLine by remember { mutableStateOf<Int?>(null) }
	var blocksRefused by remember { mutableStateOf(false) }
	var ruleRefused by remember { mutableStateOf(false) }

	val state = remember(markdownState) { markdownState.editorState }

	// A table cell holds inline text alone: no list, heading or rule goes on it, and a rule
	// would break a table a selection reaches into.
	fun readCaretLine(line: Int, selection: TextEditorRange?) {
		tableLine = line.takeIf { state.isTableCell(it) }
		isTaskActive = state.isTask(line)
		blocksRefused = state.isInlineOnlyLine(line)
		ruleRefused = blocksRefused || selection != null && (state.isTableCell(selection.start.line) || state.isTableCell(selection.end.line))
	}

	LaunchedEffect(Unit) {
		state.cursorDataFlow.collect { (position, cursorStyles, selection) ->
			// Active exactly when the toggle would remove the style.
			fun isActive(style: SpanStyle) =
				if (selection != null && selection.start != selection.end) {
					state.hasStyleThroughout(selection, style)
				} else {
					style in cursorStyles
				}
			val richSpans = if (selection != null) {
				state.getRichSpansInRange(selection)
			} else {
				state.getRichSpansAtPosition(position)
			}

			isBoldActive = isActive(state.richTextStyles.boldStyle)
			isItalicActive = isActive(state.richTextStyles.italicStyle)
			isStrikethroughActive = isActive(state.richTextStyles.strikethroughStyle)
			isHighlightActive = isActive(state.richTextStyles.highlightStyle)
			isBlockquoteActive = richSpans.any { it.style === BlockquoteSpanStyle }
			isBulletListActive = richSpans.any { it.style is BulletListSpanStyle }
			isOrderedListActive = richSpans.any { it.style is OrderedListSpanStyle }
			currentHeaderLevel = state.headerLevel(selection?.start?.line ?: position.line) ?: 0
			readCaretLine(position.line, selection)
		}
	}

	LaunchedEffect(Unit) {
		state.editOperations.collect {
			reconcileHorizontalRules(state)
			// A table edit can leave the caret where it was.
			readCaretLine(state.cursorPosition.line, state.selector.selection)
		}
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
				isHighlightActive = isHighlightActive,
				isTaskActive = isTaskActive,
				extended = extended,
				isBlockquoteActive = isBlockquoteActive,
				isBulletListActive = isBulletListActive,
				isOrderedListActive = isOrderedListActive,
				currentHeaderLevel = currentHeaderLevel,
				tableLine = tableLine,
				blocksRefused = blocksRefused,
				ruleRefused = ruleRefused,
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
	isHighlightActive: Boolean,
	isTaskActive: Boolean,
	extended: Boolean,
	isBlockquoteActive: Boolean,
	isBulletListActive: Boolean,
	isOrderedListActive: Boolean,
	currentHeaderLevel: Int,
	tableLine: Int?,
	blocksRefused: Boolean,
	ruleRefused: Boolean,
) {
	EditorTooltip("${Res.string.markdown_format_bar_bold.get()} (${shortcutHint("B")})") {
		EditorAction(
			icon = EditorIcons.IconBold,
			active = isBoldActive,
		) {
			state.toggleSpanStyle(state.richTextStyles.boldStyle)
		}
	}
	EditorTooltip("${Res.string.markdown_format_bar_italic.get()} (${shortcutHint("I")})") {
		EditorAction(
			icon = EditorIcons.IconItalic,
			active = isItalicActive,
		) {
			state.toggleSpanStyle(state.richTextStyles.italicStyle)
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
			state.toggleSpanStyle(state.richTextStyles.strikethroughStyle)
		}
	}
	if (extended) {
		EditorTooltip(Res.string.markdown_format_bar_highlight.get()) {
			EditorAction(
				icon = Icons.Default.Highlight,
				active = isHighlightActive,
			) {
				state.toggleSpanStyle(state.richTextStyles.highlightStyle)
			}
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_heading.get()) {
		EditorTextAction(
			label = if (currentHeaderLevel == 0) "H" else "H$currentHeaderLevel",
			active = currentHeaderLevel != 0,
			enabled = !blocksRefused,
		) {
			cycleHeader(state, currentHeaderLevel)
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_blockquote.get()) {
		EditorAction(
			icon = Icons.Default.FormatQuote,
			active = isBlockquoteActive,
			enabled = !blocksRefused,
		) {
			toggleBlockquote(state)
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_bullet_list.get()) {
		EditorAction(
			icon = Icons.AutoMirrored.Filled.FormatListBulleted,
			active = isBulletListActive,
			enabled = !blocksRefused,
		) {
			toggleBulletList(state)
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_numbered_list.get()) {
		EditorAction(
			icon = Icons.Default.FormatListNumbered,
			active = isOrderedListActive,
			enabled = !blocksRefused,
		) {
			toggleOrderedList(state)
		}
	}
	if (extended) {
		EditorTooltip(Res.string.markdown_format_bar_task_list.get()) {
			EditorAction(
				icon = Icons.Default.Checklist,
				active = isTaskActive,
				enabled = !blocksRefused,
			) {
				toggleTaskList(state)
			}
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_horizontal_rule.get()) {
		EditorAction(
			icon = Icons.Default.HorizontalRule,
			active = false,
			enabled = !ruleRefused,
		) {
			insertHorizontalRule(state)
		}
	}
	if (extended) TableMenu(state, tableLine)
}

/**
 * The table button: off a table it inserts one; in a table it adds and removes rows
 * and columns, aligns the caret's column, or turns the table back into text.
 */
@Composable
private fun TableMenu(state: TextEditorState, tableLine: Int?) {
	var open by remember { mutableStateOf(false) }
	Box {
		EditorTooltip(Res.string.markdown_format_bar_table.get()) {
			EditorAction(
				icon = Icons.Default.TableChart,
				active = tableLine != null,
			) {
				open = true
			}
		}
		// Not focusable, so the editor keeps focus and typing goes on in the table.
		DropdownMenu(expanded = open, onDismissRequest = { open = false }, properties = PopupProperties(focusable = false)) {
			@Composable
			fun item(label: String, action: () -> Unit) = DropdownMenuItem(text = { Text(label) }, onClick = {
				open = false
				action()
			})
			val line = tableLine
			if (line == null) {
				item(Res.string.markdown_format_bar_table_insert.get(2, 2)) { state.insertTable(rows = 2, columns = 2) }
				item(Res.string.markdown_format_bar_table_insert.get(3, 3)) { state.insertTable(rows = 3, columns = 3) }
			} else {
				val column = state.tableCellAt(line)?.column ?: 0
				item(Res.string.markdown_format_bar_table_row_above.get()) { state.insertTableRow(line, below = false) }
				item(Res.string.markdown_format_bar_table_row_below.get()) { state.insertTableRow(line, below = true) }
				item(Res.string.markdown_format_bar_table_column_left.get()) { state.insertTableColumn(line, after = false) }
				item(Res.string.markdown_format_bar_table_column_right.get()) { state.insertTableColumn(line, after = true) }
				HorizontalDivider()
				item(Res.string.markdown_format_bar_table_align_left.get()) { state.setTableColumnAlignment(line, column, TableAlignment.LEFT) }
				item(Res.string.markdown_format_bar_table_align_center.get()) { state.setTableColumnAlignment(line, column, TableAlignment.CENTER) }
				item(Res.string.markdown_format_bar_table_align_right.get()) { state.setTableColumnAlignment(line, column, TableAlignment.RIGHT) }
				HorizontalDivider()
				item(Res.string.markdown_format_bar_table_delete_row.get()) { state.deleteTableRow(line) }
				item(Res.string.markdown_format_bar_table_delete_column.get()) { state.deleteTableColumn(line) }
				item(Res.string.markdown_format_bar_table_to_text.get()) { state.convertTableToText(line) }
				item(Res.string.markdown_format_bar_table_delete.get()) { state.deleteTable(line) }
			}
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
			active = false,
			enabled = state.canUndo,
		) {
			state.undo()
		}
	}
	EditorTooltip(Res.string.markdown_format_bar_redo.get()) {
		EditorAction(
			icon = EditorIcons.IconRedo,
			active = false,
			enabled = state.canRedo,
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
		.boldShortcutModifier { state.toggleSpanStyle(state.richTextStyles.boldStyle) }
		.italicShortcutModifier { state.toggleSpanStyle(state.richTextStyles.italicStyle) }
		.strikethroughShortcutModifier { state.toggleSpanStyle(state.richTextStyles.strikethroughStyle) }
}
