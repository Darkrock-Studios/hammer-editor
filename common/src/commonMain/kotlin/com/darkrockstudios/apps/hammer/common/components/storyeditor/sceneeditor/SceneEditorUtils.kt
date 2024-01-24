package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor

import korlibs.memory.clamp

val MIN_FONT_SIZE = 8f
val MAX_FONT_SIZE = 32f

fun increaseEditorTextSize(currentSize: Float): Float {
	return (currentSize + 1f).clamp(MIN_FONT_SIZE, MAX_FONT_SIZE)
}

fun decreaseEditorTextSize(currentSize: Float): Float {
	return (currentSize - 1f).clamp(MIN_FONT_SIZE, MAX_FONT_SIZE)
}