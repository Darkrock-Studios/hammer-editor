package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.preview.fakeSceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SceneMetadataPanelUi

@Preview
@Composable
private fun SceneMetadataPanelUiPreview() {
	SceneMetadataPanelUi(
		component = object : SceneMetadataPanel {
			override val state = MutableValue(
				SceneMetadataPanel.State(
					sceneItem = fakeSceneItem(),
					wordCount = 1337,
					metadata = SceneMetadata(
						outline = "",
						notes = ""
					)
				)
			)

			override fun updateOutline(text: String) {}
			override fun updateNotes(text: String) {}
		},
		closeMetadata = {}
	)
}