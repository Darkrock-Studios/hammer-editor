package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.ui.text.AnnotatedString
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.markdown.toMarkdown

data class ComposeRichText(
	val state: MarkdownExtension? = null,
	val snapshot: AnnotatedString? = null
) : PlatformRichText {

	init {
		if (state == null && snapshot == null) error("ComposeRichText must have non-null data")
	}

	override fun convertToMarkdown(): String {
		return when {
			snapshot != null -> snapshot.toMarkdown()
			state != null -> state.exportAsMarkdown()
			else -> error("ComposeRichText must contain non-null data ")
		}
	}

	fun getAnnotatedString(): AnnotatedString {
		return when {
			snapshot != null -> snapshot
			state != null -> state.editorState.getAllText()
			else -> error("ComposeRichText must contain non-null data ")
		}
	}

	override fun compare(text: PlatformRichText): Boolean {
		return if (text is ComposeRichText) {
			text.snapshot == snapshot
		} else {
			false
		}
	}

	override fun equals(other: Any?): Boolean {
		return if (other is PlatformRichText) {
			compare(other)
		} else {
			false
		}
	}
}
