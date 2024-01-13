package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.scenemetadatarepository.SceneMetadata
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface SceneMetadataPanel : HammerComponent {
	val state: Value<State>

	fun updateOutline(text: String)
	fun updateNotes(text: String)

	data class State(
		val sceneItem: SceneItem,
		val wordCount: Int = 0,
		val metadata: SceneMetadata = SceneMetadata(),
	)
}