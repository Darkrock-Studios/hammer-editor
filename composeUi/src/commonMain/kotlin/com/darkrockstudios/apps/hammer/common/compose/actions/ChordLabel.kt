package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.Keymap
import com.darkrockstudios.apps.hammer.common.components.actions.currentChordPlatform
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText

/** Chords show on desktop, and on touch platforms once a hardware keyboard is in use. */
@Composable
fun rememberShowChords(): Boolean =
	currentChordPlatform.showsChordsByDefault || LocalInputModeManager.current.inputMode == InputMode.Keyboard

/** The primary chord bound to [id], or nothing when chords are hidden or [id] has none. */
@Composable
fun ChordLabel(id: ActionId, modifier: Modifier = Modifier) {
	if (!rememberShowChords()) return
	val keymap = LocalActionDispatcher.current?.keymap ?: Keymap.Default
	val chord = keymap.primaryChord(id) ?: return
	HdMetaText(text = chord.label(), modifier = modifier, maxLines = 1)
}
