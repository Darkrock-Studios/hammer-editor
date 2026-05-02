package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.TextEditorRange
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.richstyle.HR_PLACEHOLDER
import com.darkrockstudios.texteditor.richstyle.HorizontalRuleSpanStyle
import com.darkrockstudios.texteditor.state.TextEditorState

internal fun insertLineBullet(state: TextEditorState) {
	val saved = state.cursorPosition
	state.cursor.updatePosition(CharLineOffset(saved.line, 0))
	state.insertStringAtCursor("• ")
	state.cursor.updatePosition(CharLineOffset(saved.line, saved.char + 2))
}

internal fun insertHorizontalRule(state: TextEditorState) {
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

internal val HEADER_CYCLE_LEVELS = 1..3

internal fun cycleHeader(
	state: TextEditorState,
	markdown: MarkdownExtension,
	currentLevel: Int,
) {
	val maxLevel = HEADER_CYCLE_LEVELS.last
	val nextLevel = (currentLevel + 1) % (maxLevel + 1)
	val selection = state.selector.selection
	if (selection != null) {
		HEADER_CYCLE_LEVELS.forEach { lvl ->
			state.removeStyleSpan(selection, markdown.markdownStyles.header(lvl))
		}
		if (nextLevel != 0) {
			state.addStyleSpan(selection, markdown.markdownStyles.header(nextLevel))
		}
	} else {
		HEADER_CYCLE_LEVELS.forEach { lvl ->
			state.cursor.removeStyle(markdown.markdownStyles.header(lvl))
		}
		if (nextLevel != 0) {
			state.cursor.addStyle(markdown.markdownStyles.header(nextLevel))
		}
	}
}
