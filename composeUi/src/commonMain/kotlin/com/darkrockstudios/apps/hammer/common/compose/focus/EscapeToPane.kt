package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import com.darkrockstudios.apps.hammer.common.compose.actions.LocalActionDispatcher

/**
 * The Esc ladder's steps inside a pane, ahead of the window's back. Goes before the pane's
 * [focusPane] in the chain, so a press on the focused container reaches it too.
 *
 * An Esc press from inside the pane that nothing below took is offered to [onEscape]. When it
 * returns true the press is consumed and the input mode turns to keyboard, so the list's cursor
 * shows. A consumed press never reaches the window's `EscapeRouter`, whose release then does
 * nothing, so one tap takes one step. It acts on the press, as the router does for overlays:
 * with an overlay up (a menu that left focus here) the press is left to the router to close it.
 */
@Composable
fun Modifier.escapeToPane(onEscape: () -> Boolean): Modifier {
	val actions = LocalActionDispatcher.current
	val inputModeManager = LocalInputModeManager.current
	val escape by rememberUpdatedState(onEscape)
	return onKeyEvent { event ->
		if (event.key != Key.Escape || event.type != KeyEventType.KeyDown) return@onKeyEvent false
		if (actions?.hasOverlay() == true) return@onKeyEvent false
		escape().also { stepped -> if (stepped) inputModeManager.markKeyboardInput() }
	}
}

/** [escapeToPane] for a pane on its own: Esc first leaves the text field, then goes back. */
@Composable
fun Modifier.escapeToContainer(id: PaneId): Modifier {
	val paneFocus = LocalPaneFocus.current ?: return this
	return escapeToPane { paneFocus.escapeToContainer(id) }
}

/**
 * [escapeToPane] for a form: the first Esc is taken and does nothing more, so focus stays put and a
 * rich text field's own Esc-then-Tab can move on to the next field. A second Esc in a row goes
 * back. Any other key between them starts over.
 */
@Composable
fun Modifier.escapeTwiceToLeave(): Modifier {
	val armed = remember { BooleanArray(1) }
	return onPreviewKeyEvent { event ->
		if (event.type == KeyEventType.KeyDown && event.key != Key.Escape) armed[0] = false
		false
	}.escapeToPane {
		val second = armed[0]
		armed[0] = !second
		!second
	}
}
