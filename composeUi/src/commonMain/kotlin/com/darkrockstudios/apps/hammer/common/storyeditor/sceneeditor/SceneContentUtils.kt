package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.ui.text.AnnotatedString
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.texteditor.find.FindCurrentMatchStyle
import com.darkrockstudios.texteditor.find.FindMatchStyle
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.toAnnotatedStringFromMarkdown
import com.darkrockstudios.texteditor.richstyle.HighlightSpanStyle
import com.darkrockstudios.texteditor.richstyle.SpellCheckStyle
import com.darkrockstudios.texteditor.state.TextEditOperation

fun getInitialEditorContent(
	sceneContent: SceneContent?,
	markdownConfig: MarkdownConfiguration
): AnnotatedString {
	return if (sceneContent != null) {
		val composeText = sceneContent.platformRepresentation as? ComposeRichText
		val markdown = sceneContent.markdown
		if (composeText != null) {
			composeText.getAnnotatedString()
		} else if (markdown != null) {
			markdown.toAnnotatedStringFromMarkdown(markdownConfig)
		} else {
			error("Should be impossible to not have either platform rep or markdown")
		}
	} else {
		AnnotatedString("")
	}
}

/**
 * The editor emits every mutation on `editOperations`, including ephemeral decoration
 * spans (spell-check underlines, find highlights) that never serialize to markdown.
 * These must not mark the scene buffer dirty, or an async spell-check pass re-dirties
 * a freshly-saved scene.
 */
fun TextEditOperation.isDecorationOnly(): Boolean {
	if (this !is TextEditOperation.RichSpan) return false
	return when (style) {
		is SpellCheckStyle,
		is HighlightSpanStyle,
		is FindMatchStyle,
		is FindCurrentMatchStyle -> true

		else -> false
	}
}