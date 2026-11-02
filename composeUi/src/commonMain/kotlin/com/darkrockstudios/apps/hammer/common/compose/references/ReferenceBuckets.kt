package com.darkrockstudios.apps.hammer.common.compose.references

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.scene_editor_metadata_references_characters_label
import com.darkrockstudios.apps.hammer.scene_editor_metadata_references_locations_label
import com.darkrockstudios.apps.hammer.scene_editor_metadata_references_other_label
import com.darkrockstudios.apps.hammer.scene_editor_metadata_references_scenes_label

/** The groups a list of references is shown in, in display order. */
enum class ReferenceBucket { Characters, Locations, Other, Scenes }

fun EntryType.referenceBucket(): ReferenceBucket = when (this) {
	EntryType.PERSON -> ReferenceBucket.Characters
	EntryType.PLACE -> ReferenceBucket.Locations
	else -> ReferenceBucket.Other
}

@Composable
fun ReferenceBucket.label(): String = when (this) {
	ReferenceBucket.Characters -> Res.string.scene_editor_metadata_references_characters_label
	ReferenceBucket.Locations -> Res.string.scene_editor_metadata_references_locations_label
	ReferenceBucket.Other -> Res.string.scene_editor_metadata_references_other_label
	ReferenceBucket.Scenes -> Res.string.scene_editor_metadata_references_scenes_label
}.get()
