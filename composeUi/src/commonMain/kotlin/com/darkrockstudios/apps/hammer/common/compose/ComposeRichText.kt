package com.darkrockstudios.apps.hammer.common.compose

import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.text.toMarkdown
import com.darkrockstudios.richtexteditor.utils.RichTextValueSnapshot

data class ComposeRichText(val snapshot: RichTextValueSnapshot) : PlatformRichText {
	override fun convertToMarkdown() = snapshot.toMarkdown()

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