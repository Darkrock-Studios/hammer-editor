package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.compose.references.ReferenceBucket
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.AddReferenceCard

/** Adding an encyclopedia reference from the scene info panel, over a scrim. */
@Composable
internal fun AddReferenceScrimPreview() {
	val project = fakeProjectDef()
	fun person(id: Int, name: String, dismissed: Boolean = false) =
		SceneMetadataPanel.AddSuggestion(EntryDef(project, id, EntryType.PERSON, name), dismissed)
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		AddReferenceCard(
			tab = ReferenceBucket.Characters,
			onTabChange = {},
			query = "",
			onQueryChange = {},
			suggestions = listOf(
				person(1, "Alandra Vey"),
				person(2, "Bastion Roke"),
				person(3, "The Ferryman", dismissed = true),
			),
			onAdd = {},
			onClose = {},
		)
	}
}
