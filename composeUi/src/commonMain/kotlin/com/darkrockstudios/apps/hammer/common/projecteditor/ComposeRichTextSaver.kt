package com.darkrockstudios.apps.hammer.common.projecteditor

import androidx.compose.runtime.saveable.Saver
import com.darkrockstudios.apps.hammer.common.data.text.markdownToSnapshot
import com.darkrockstudios.apps.hammer.common.data.text.toMarkdown
import com.darkrockstudios.richtexteditor.model.RichTextValue

val ComposeRichTextSaver = Saver<RichTextValue, String>(
	save = {
		it.getLastSnapshot().toMarkdown()
	},
	restore = {
		val rtv = RichTextValue.fromSnapshot(it.markdownToSnapshot())
		return@Saver RichTextValue.fromSnapshot(rtv.getLastSnapshot())
	}
)