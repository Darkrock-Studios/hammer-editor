package com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.scenemetadata

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface SceneMetadata : HammerComponent {
	val state: Value<State>

	data class State(
		val sceneItem: SceneItem,
		val wordCount: Int = 0
	)
}