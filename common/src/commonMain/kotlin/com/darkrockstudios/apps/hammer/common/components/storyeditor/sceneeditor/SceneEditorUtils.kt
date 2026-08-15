package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import korlibs.memory.clamp

val MIN_FONT_SIZE = 8f
val MAX_FONT_SIZE = 32f

fun increaseEditorTextSize(currentSize: Float): Float {
	return (currentSize + 1f).clamp(MIN_FONT_SIZE, MAX_FONT_SIZE)
}

fun decreaseEditorTextSize(currentSize: Float): Float {
	return (currentSize - 1f).clamp(MIN_FONT_SIZE, MAX_FONT_SIZE)
}

fun clampEditorWidth(width: Float): Float {
	return width.clamp(GlobalSettings.MIN_EDITOR_WIDTH, GlobalSettings.MAX_EDITOR_WIDTH)
}