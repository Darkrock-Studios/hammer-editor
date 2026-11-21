package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.AddTagCard

/** Adding tags from the scene info panel mid-word, so suggestions show, over a scrim. */
@Composable
internal fun AddTagScrimPreview() {
	PreviewScrim {
		AddTagCard(
			draft = "act-two po",
			onDraftChange = {},
			suggestions = listOf("pov/alandra", "pov/bastion"),
			onSelectSuggestion = {},
			onDone = {},
		)
	}
}
