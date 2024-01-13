package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.text.markdownToSnapshot
import com.darkrockstudios.richtexteditor.model.RichTextValue

fun getInitialEditorContent(sceneContent: SceneContent?): RichTextValue {
	return if (sceneContent != null) {
		val composeText = sceneContent.platformRepresentation as? ComposeRichText
		val markdown = sceneContent.markdown
		if (composeText != null) {
			RichTextValue.fromSnapshot(composeText.snapshot)
		} else if (markdown != null) {
			RichTextValue.fromSnapshot(markdown.markdownToSnapshot())
		} else {
			throw IllegalStateException("Should be impossible to not have either platform rep or markdown")
		}
	} else {
		RichTextValue.get()
	}
}