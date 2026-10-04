package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.LocalEditorTextStyle
import com.darkrockstudios.apps.hammer.common.compose.LocalRichTextStyles
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.withParagraphIndent
import com.darkrockstudios.texteditor.RichTextView
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.rememberTextEditorStyle
import com.darkrockstudios.texteditor.state.rememberTextEditorState

/**
 * Read-only renderer for a markdown [String]. Wraps the library's [RichTextView] so
 * inline styling (bold, italic, headers, links, strikethrough) renders through the same
 * pipeline as the editor — no surface, no scrollbar, height wraps to content.
 *
 * The raw [markdown] is exposed via semantics so test matchers (`onNodeWithText`,
 * `assertTextSatisfies`) can read it; the Canvas-based render itself has no semantic text.
 *
 * [importDuringComposition] parses the first text in composition instead of a frame later,
 * so the view has its real height on the first frame. A lazy list cannot scroll past a row
 * that is still empty.
 *
 * [isSelectable] lets the user select and copy text.
 */
@Composable
fun MarkdownView(
	markdown: String,
	modifier: Modifier = Modifier,
	contentPadding: PaddingValues = PaddingValues(0.dp),
	importDuringComposition: Boolean = false,
	isSelectable: Boolean = false,
) {
	val richTextStyles = LocalRichTextStyles.current
	val state = rememberTextEditorState()
	var imported by remember(state) { mutableStateOf(if (importDuringComposition) markdown else null) }
	val markdownExtension = remember(state) {
		state.richTextStyles = richTextStyles
		state.withMarkdown(HammerMarkdownConfiguration).apply {
			if (importDuringComposition) importMarkdown(markdown)
		}
	}
	LaunchedEffect(markdownExtension, markdown) {
		if (imported != markdown) {
			markdownExtension.importMarkdown(markdown)
			imported = markdown
		}
	}
	RichTextView(
		state = state,
		modifier = modifier.semantics { text = AnnotatedString(markdown) },
		contentPadding = contentPadding,
		isSelectable = isSelectable,
		style = rememberTextEditorStyle(
			textStyle = LocalEditorTextStyle.current.withParagraphIndent()
		)
	)
}
