package com.darkrockstudios.apps.hammer.common.components.actions

/**
 * Spots two quick taps of Shift, which open the palette. A tap is a press and release of either
 * Shift, not held. Any other key between the taps, or a pointer press (Shift+click), starts over,
 * so typing capitals or extending a selection never counts. Hosts feed it every key event they
 * see and [reset] it on pointer presses.
 */
class DoubleShiftDetector {
	private var downAt: Long? = null
	private var held = false
	private var lastTapUpAt: Long? = null

	/** True when this event completes a double tap. Times are any monotonic milliseconds. */
	fun onKey(isShift: Boolean, isDown: Boolean, atMs: Long): Boolean {
		if (!isShift) {
			reset()
			return false
		}
		if (isDown) {
			// A second press with no release between is the key repeating while held.
			if (downAt != null) held = true else downAt = atMs
			return false
		}
		val pressedAt = downAt ?: return false
		downAt = null
		val wasHeld = held || atMs - pressedAt > MAX_HOLD_MS
		held = false
		if (wasHeld) {
			lastTapUpAt = null
			return false
		}
		val previous = lastTapUpAt
		if (previous != null && pressedAt - previous <= MAX_GAP_MS) {
			lastTapUpAt = null
			return true
		}
		lastTapUpAt = atMs
		return false
	}

	/** [onKey], opening the palette through [actions] when the event completes a double tap. */
	fun feed(isShift: Boolean, isDown: Boolean, atMs: Long, actions: ActionDispatcher?) {
		if (onKey(isShift, isDown, atMs)) actions?.invoke(HammerActions.PaletteOpen.id)
	}

	fun reset() {
		downAt = null
		held = false
		lastTapUpAt = null
	}

	companion object {
		const val MAX_GAP_MS = 400L
		const val MAX_HOLD_MS = 300L
	}
}
