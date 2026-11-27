package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatcher

/**
 * A window's Esc. It acts in the window's `onKeyEvent`, so content that uses Esc (a find bar, a
 * menu) keeps it. A press nothing took closes the topmost overlay
 * ([ActionDispatcher.dismissTopOverlay]) and is consumed, so the host's own dialog back never
 * sees it as well. A release calls [back] only when its press reached the window with no
 * overlay up and none is up now; a release without its press does nothing.
 */
class EscapeRouter(
	private val actions: ActionDispatcher,
	private val back: () -> Boolean,
) {
	private var held = false
	private var repeat = false
	private var pressed = false
	private var pressHitOverlay = false

	/**
	 * Feed from the window's `onPreviewKeyEvent`; never consumes. It sees every Esc, so a held
	 * key's repeats and a release content consumed are told apart from a fresh press.
	 */
	fun onPreviewKeyEvent(event: KeyEvent): Boolean {
		if (event.key != Key.Escape) return false
		when (event.type) {
			KeyEventType.KeyDown -> {
				repeat = held
				held = true
				if (!repeat) pressed = false
			}

			KeyEventType.KeyUp -> held = false
		}
		return false
	}

	fun onKeyEvent(event: KeyEvent): Boolean {
		if (event.key != Key.Escape) return false
		return when (event.type) {
			KeyEventType.KeyDown -> onPress()
			KeyEventType.KeyUp -> onRelease()
			else -> false
		}
	}

	private fun onPress(): Boolean {
		// One press closes one overlay, however long the key is held.
		if (repeat) return pressed && pressHitOverlay
		pressed = true
		pressHitOverlay = actions.hasOverlay()
		if (pressHitOverlay) actions.dismissTopOverlay()
		return pressHitOverlay
	}

	private fun onRelease(): Boolean {
		if (!pressed) return false
		pressed = false
		if (pressHitOverlay || actions.hasOverlay()) return true
		return back()
	}
}
