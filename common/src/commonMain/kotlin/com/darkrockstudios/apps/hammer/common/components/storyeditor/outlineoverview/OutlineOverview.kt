package com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.SceneItem

interface OutlineOverview {
	val state: Value<State>

	fun dismiss()

	data class State(
		val overview: List<OutlineItem> = emptyList(),
	)

	sealed class OutlineItem {
		data class ChapterOutline(
			val sceneItem: SceneItem,
		) : OutlineItem()

		data class SceneOutline(
			val sceneItem: SceneItem,
			val outline: String?
		) : OutlineItem()
	}
}