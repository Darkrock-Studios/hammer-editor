package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.TextEditorRange
import com.darkrockstudios.texteditor.richstyle.HR_PLACEHOLDER
import com.darkrockstudios.texteditor.richstyle.HorizontalRuleSpanStyle
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.headerLevel
import com.darkrockstudios.texteditor.state.isTableCell
import com.darkrockstudios.texteditor.state.toggleBlockquote
import com.darkrockstudios.texteditor.state.toggleBulletList
import com.darkrockstudios.texteditor.state.toggleHeader
import com.darkrockstudios.texteditor.state.toggleOrderedList
import com.darkrockstudios.texteditor.state.toggleTaskList

internal fun insertHorizontalRule(state: TextEditorState) {
	// A table cell holds inline text alone, and a rule would break the table apart.
	val selection = state.selector.selection
	val touched = selection?.let { listOf(it.start.line, it.end.line) } ?: listOf(state.cursorPosition.line)
	if (touched.any { state.isTableCell(it) }) return
	state.insertNewlineAtCursor()
	val hrLine = state.cursorPosition.line
	state.insertStringAtCursor(HR_PLACEHOLDER)
	state.insertNewlineAtCursor()
	state.addRichSpan(
		start = CharLineOffset(hrLine, 0),
		end = CharLineOffset(hrLine, HR_PLACEHOLDER.length),
		style = HorizontalRuleSpanStyle,
	)
}

// Once a user types on an HR line, the placeholder space is gone — drop the rule and
// strip the tracked placeholder so the line becomes plain text. A proper fix needs
// block-level support in the editor.
internal fun reconcileHorizontalRules(state: TextEditorState) {
	val hrSpans = state.richSpanManager.getAllRichSpans()
		.filter { it.style === HorizontalRuleSpanStyle }
	if (hrSpans.isEmpty()) return
	hrSpans.forEach { span ->
		val lineIndex = span.range.start.line
		val lineText = state.textLines.getOrNull(lineIndex)?.text ?: return@forEach
		if (lineText == HR_PLACEHOLDER) return@forEach

		val placeholderChar = span.range.start.char
		val deleteAt = if (lineText.getOrNull(placeholderChar) == ' ') {
			placeholderChar
		} else {
			lineText.indexOf(' ').takeIf { it >= 0 }
		}
		if (deleteAt != null) {
			state.delete(
				TextEditorRange(
					start = CharLineOffset(lineIndex, deleteAt),
					end = CharLineOffset(lineIndex, deleteAt + 1),
				)
			)
		}
		state.removeRichSpan(span)
	}
}

private fun selectedLines(state: TextEditorState): IntRange {
	val selection = state.selector.selection
	return if (selection != null) {
		selection.start.line..selection.end.line
	} else {
		state.cursorPosition.line..state.cursorPosition.line
	}
}

internal fun toggleBlockquote(state: TextEditorState) {
	state.toggleBlockquote(selectedLines(state))
}

internal fun toggleBulletList(state: TextEditorState) {
	state.toggleBulletList(selectedLines(state))
}

internal fun toggleOrderedList(state: TextEditorState) {
	state.toggleOrderedList(selectedLines(state))
}

internal fun toggleTaskList(state: TextEditorState) {
	state.toggleTaskList(selectedLines(state))
}

internal val HEADER_CYCLE_LEVELS = 1..3

internal fun cycleHeader(state: TextEditorState, currentLevel: Int) {
	val lines = selectedLines(state)
	if (currentLevel >= HEADER_CYCLE_LEVELS.last) {
		// toggleHeader only removes a level every line already has, so clear each line by its own
		lines.forEach { line ->
			state.headerLevel(line)?.let { state.toggleHeader(line..line, it) }
		}
	} else {
		state.toggleHeader(lines, currentLevel + 1)
	}
}
