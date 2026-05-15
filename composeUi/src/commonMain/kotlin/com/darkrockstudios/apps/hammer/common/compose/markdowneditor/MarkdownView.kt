package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.LocalMarkdownConfig
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
 */
@Composable
fun MarkdownView(
	markdown: String,
	modifier: Modifier = Modifier,
	contentPadding: PaddingValues = PaddingValues(0.dp),
) {
	val markdownConfig = LocalMarkdownConfig.current
	val state = rememberTextEditorState()
	val markdownExtension = remember(state) { state.withMarkdown(markdownConfig) }
	LaunchedEffect(markdownExtension, markdown) {
		markdownExtension.importMarkdown(markdown)
	}
	RichTextView(
		state = state,
		modifier = modifier.semantics { text = AnnotatedString(markdown) },
		contentPadding = contentPadding,
		style = rememberTextEditorStyle(
			textStyle = TextStyle.Default.copy(
				textIndent = TextIndent(firstLine = 24.sp)
			)
		)
	)
}
