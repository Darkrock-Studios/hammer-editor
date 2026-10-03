package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import korlibs.memory.clamp

/** The body text sizes the editor steps through. A size off the ladder snaps to it on the next step. */
val EDITOR_TEXT_SIZES = listOf(12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f)

fun increaseEditorTextSize(currentSize: Float): Float =
	EDITOR_TEXT_SIZES.firstOrNull { it > currentSize } ?: EDITOR_TEXT_SIZES.last()

fun decreaseEditorTextSize(currentSize: Float): Float =
	EDITOR_TEXT_SIZES.lastOrNull { it < currentSize } ?: EDITOR_TEXT_SIZES.first()

fun clampEditorWidth(width: Float): Float {
	return width.clamp(GlobalSettings.MIN_EDITOR_WIDTH, GlobalSettings.MAX_EDITOR_WIDTH)
}
