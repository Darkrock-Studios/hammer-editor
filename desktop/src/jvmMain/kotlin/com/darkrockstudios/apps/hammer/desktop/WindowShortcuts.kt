package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.input.key.*
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.actions.currentChordPlatform
import com.darkrockstudios.apps.hammer.common.compose.actions.toKeyChord

/** Shortcuts of the project selection window, which has no action registry. */
internal enum class WindowShortcut {
	Back,
	Quit,
}

internal fun KeyEvent.toProjectSelectionShortcut(platform: ChordPlatform = currentChordPlatform): WindowShortcut? =
	when {
		key == Key.Escape && type == KeyEventType.KeyUp -> WindowShortcut.Back
		toKeyChord(platform) == KeyChord.mod(ChordKey.Q) -> WindowShortcut.Quit
		else -> null
	}
