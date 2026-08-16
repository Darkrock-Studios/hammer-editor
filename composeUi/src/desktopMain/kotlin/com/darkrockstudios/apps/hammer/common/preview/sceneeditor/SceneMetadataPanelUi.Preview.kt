package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.fakeSceneItem
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SceneMetadataPanelUi

private fun previewEntryDef(id: Int, name: String, type: EntryType) = EntryDef(
	projectDef = fakeProjectDef(),
	id = id,
	name = name,
	type = type,
)

private val previewConfirmedRefs = listOf(
	previewEntryDef(1, "Alandra Vey", EntryType.PERSON),
	previewEntryDef(2, "Bastion Roke", EntryType.PERSON),
	previewEntryDef(3, "The Sunken Quarter", EntryType.PLACE),
	previewEntryDef(4, "Cinderhall", EntryType.PLACE),
	previewEntryDef(5, "The Ashen Key", EntryType.THING),
	previewEntryDef(6, "The Long Silence", EntryType.EVENT),
	previewEntryDef(7, "Debt As Inheritance", EntryType.IDEA),
).sortedBy { it.name.lowercase() }

private val previewDismissedRefs = listOf(
	previewEntryDef(8, "Marek", EntryType.PERSON),
	previewEntryDef(9, "Tidewatch Lantern", EntryType.THING),
)

private fun previewComponent(
	confirmedRefs: List<EntryDef>,
	dismissedRefs: List<EntryDef>,
) = object : SceneMetadataPanel {
	override val state = MutableValue(
		SceneMetadataPanel.State(
			sceneItem = fakeSceneItem(),
			wordCount = 1337,
			metadata = SceneMetadata(
				outline = "Alandra bargains for passage through the Sunken Quarter.",
				notes = "",
				tags = setOf("act-one", "pov/alandra"),
			),
			confirmedRefs = confirmedRefs,
			dismissedRefs = dismissedRefs,
		)
	)

	override fun updateOutline(text: String) {}
	override fun updateNotes(text: String) {}
	override fun updateDraftName(text: String) {}
	override fun validateDraftName(text: String) = true
	override fun dismissReference(entryId: Int) {}
	override fun restoreDismissedReference(entryId: Int) {}
	override fun addConfirmedReference(entryId: Int) {}
	override fun searchEntriesForAdd(query: String, types: Set<EntryType>, maxResults: Int) =
		emptyList<SceneMetadataPanel.AddSuggestion>()

	override fun navigateToEntry(entryDef: EntryDef) {}
	override fun addTags(input: String) {}
	override fun removeTag(tag: String) {}
	override fun showGlobalSearchForTag(tag: String) {}
	override fun suggestTags(prefix: String, limit: Int) = emptyList<String>()
}

@Preview
@Composable
fun SceneMetadataPanelUiPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			SceneMetadataPanelUi(
				component = previewComponent(previewConfirmedRefs, previewDismissedRefs),
				closeMetadata = {},
			)
		}
	}
}

@Preview
@Composable
fun SceneMetadataPanelUiEmptyPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			SceneMetadataPanelUi(
				component = previewComponent(emptyList(), emptyList()),
				closeMetadata = {},
			)
		}
	}
}
