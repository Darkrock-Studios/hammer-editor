package com.darkrockstudios.apps.hammer.common.components.actions

/**
 * A host's chord presses, so a held key's auto-repeats run an action again only when it
 * [ActionDef.repeats]. Any other repeat is taken or passed on as its first press was: a held toggle
 * never flips back, and a key the first press left to the focused widget still goes there.
 */
class ChordRepeats {
	private var lastKey: Any? = null
	private var lastTaken = false

	/**
	 * Dispatches [chord], made by a press of the physical key [key], and returns whether it was
	 * taken. [isRepeat] marks an auto-repeat; one of a key other than the last pressed runs as a
	 * fresh press, as the host missed that key's first press.
	 */
	fun press(actions: ActionDispatcher, key: Any, chord: KeyChord?, isRepeat: Boolean): Boolean {
		if (isRepeat && key == lastKey && !repeats(actions, chord)) return lastTaken
		lastKey = key
		lastTaken = chord != null && actions.dispatch(chord)
		return lastTaken
	}

	private fun repeats(actions: ActionDispatcher, chord: KeyChord?): Boolean {
		val id = chord?.let(actions.keymap::actionFor) ?: return false
		return HammerActions.byId(id)?.repeats == true
	}
}
