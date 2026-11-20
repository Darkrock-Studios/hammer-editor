package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.AddTagCard

/** Adding tags from the scene info panel mid-word, so suggestions show, over a scrim. */
@Composable
internal fun AddTagScrimPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		AddTagCard(
			draft = "act-two po",
			onDraftChange = {},
			suggestions = listOf("pov/alandra", "pov/bastion"),
			onSelectSuggestion = {},
			onDone = {},
		)
	}
}
