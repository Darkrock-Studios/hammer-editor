package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatcher
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import com.darkrockstudios.apps.hammer.common.components.actions.ChordRepeats
import com.darkrockstudios.apps.hammer.common.components.actions.currentChordPlatform

/**
 * A window's chords, fed from its `onPreviewKeyEvent`. Desktop key events carry no auto-repeat
 * flag, so a press of the key already down is taken as its repeat ([ChordRepeats]).
 */
class WindowChords(
	private val actions: ActionDispatcher,
	private val platform: ChordPlatform = currentChordPlatform,
) {
	private val repeats = ChordRepeats()
	private var down: Key? = null

	fun onPreviewKeyEvent(event: KeyEvent): Boolean = when (event.type) {
		KeyEventType.KeyDown -> {
			val isRepeat = event.key == down
			down = event.key
			repeats.press(actions, event.key, event.toKeyChord(platform), isRepeat)
		}

		KeyEventType.KeyUp -> {
			if (event.key == down) down = null
			false
		}

		else -> false
	}
}
