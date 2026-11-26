package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.input.key.KeyEvent
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.actions.currentChordPlatform
import com.darkrockstudios.apps.hammer.common.compose.actions.toKeyChord

/** Mod+Q in the project selection window, which has no action table. */
internal fun KeyEvent.isProjectSelectionQuit(platform: ChordPlatform = currentChordPlatform): Boolean =
	toKeyChord(platform) == KeyChord.mod(ChordKey.Q)
