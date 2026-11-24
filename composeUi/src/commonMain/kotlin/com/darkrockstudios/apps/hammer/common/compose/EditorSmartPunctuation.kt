package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import com.darkrockstudios.texteditor.behaviors.SmartPunctuation
import com.darkrockstudios.texteditor.state.TextEditorState

/** Whether typed text gets [SmartPunctuation]; the user's global setting. */
val LocalSmartPunctuation = compositionLocalOf { false }

/** Keeps [state]'s [SmartPunctuation] in step with [LocalSmartPunctuation]. */
@Composable
internal fun ApplySmartPunctuation(state: TextEditorState) {
	val enabled = LocalSmartPunctuation.current
	DisposableEffect(state, enabled) {
		// Each editor gets its own instance: SmartPunctuation remembers its last substitution.
		val behavior = if (enabled) SmartPunctuation().also { state.editBehaviors += it } else null
		onDispose { behavior?.let { state.editBehaviors.remove(it) } }
	}
}
