package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.ArchivedScenesCard

private fun archivedScene(id: Int, name: String) = SceneItem(
	projectDef = fakeProjectDef(),
	type = SceneItem.Type.Scene,
	id = id,
	name = name,
	order = id,
)

/** The archived scenes list, over a scrim. */
@Composable
internal fun ArchivedScenesScrimPreview() {
	ArchivedScenesScrim(
		listOf(
			archivedScene(4, "Pig and Pepper"),
			archivedScene(9, "The Lobster Quadrille"),
			archivedScene(12, "Who Stole the Tarts?"),
		)
	)
}

/** The archived scenes list with nothing archived, over a scrim. */
@Composable
internal fun ArchivedScenesEmptyScrimPreview() {
	ArchivedScenesScrim(emptyList())
}

@Composable
private fun ArchivedScenesScrim(scenes: List<SceneItem>) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		ArchivedScenesCard(archivedScenes = scenes, onUnarchive = {}, onClose = {})
	}
}
