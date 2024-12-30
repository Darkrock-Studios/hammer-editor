package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.ui.text.AnnotatedString
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.texteditor.markdown.toAnnotatedStringFromMarkdown

fun getInitialEditorContent(sceneContent: SceneContent?): AnnotatedString? {
	return if (sceneContent != null) {
		val composeText = sceneContent.platformRepresentation as? ComposeRichText
		val markdown = sceneContent.markdown
		if (composeText != null) {
			composeText.getAnnotatedString()
		} else if (markdown != null) {
			markdown.toAnnotatedStringFromMarkdown()
		} else {
			error("Should be impossible to not have either platform rep or markdown")
		}
	} else {
		AnnotatedString("")
	}
}