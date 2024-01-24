package com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings

interface FocusMode {
	val state: Value<State>
	var lastForceUpdate: MutableValue<Long>

	fun dismiss()
	fun onContentChanged(content: PlatformRichText)
	fun decreaseTextSize()
	fun increaseTextSize()
	fun resetTextSize()

	data class State(
		val projectDef: ProjectDef,
		val sceneItem: SceneItem,
		val sceneBuffer: SceneBuffer? = null,
		val textSize: Float = GlobalSettings.DEFAULT_FONT_SIZE
	)
}