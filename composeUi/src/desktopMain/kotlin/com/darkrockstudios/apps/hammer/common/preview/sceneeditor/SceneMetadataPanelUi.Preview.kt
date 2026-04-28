package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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
			override fun updateDraftName(text: String) {}
			override fun validateDraftName(text: String) = true
			override fun dismissReference(entryId: Int) {}
			override fun restoreDismissedReference(entryId: Int) {}
			override fun navigateToEntry(entryDef: com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef) {}
		},
		closeMetadata = {}
	)
}