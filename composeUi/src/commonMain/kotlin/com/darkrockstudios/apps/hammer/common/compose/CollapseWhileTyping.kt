package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

// Wait for the keyboard's show animation to finish before collapsing — removing content while it
// animates in cancels the editor's input session and dismisses the keyboard on the first tap.
private const val KEYBOARD_SETTLE_MS = 350L

/**
 * Wraps chrome that should collapse away while the markdown body editor is being typed into, to
 * free vertical space on small screens. This is the one place the rules live, so any field can be
 * made collapsible by wrapping it rather than re-deriving the condition at each call site.
 *
 * - Collapses only once the keyboard has fully animated in (debounced); re-appears immediately
 *   when the keyboard goes away.
 * - Snaps rather than animates: animating the size change forces the (potentially large) body
 *   editor to reflow every frame, which can stall the main thread right after a tap and get the
 *   tap misread as a long-press. A single snap reflow avoids that.
 * - Inherently mobile-only: desktop/web report no IME inset, so [isImeVisible] is always false
 *   there and nothing collapses.
 *
 * @param enabled set false to never collapse (e.g. when not in an editing mode).
 * @param keepVisible pass a focusable field's own focus state here so it stays reachable — tapping
 *   it raises the keyboard but it won't vanish out from under you. Leave false for pure decoration
 *   (crumb rows, dividers, mastheads), which is never focused.
 */
@Composable
fun CollapseWhileTyping(
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	keepVisible: Boolean = false,
	content: @Composable () -> Unit,
) {
	val shouldHide = enabled && isImeVisible() && !keepVisible
	var hidden by remember { mutableStateOf(false) }
	LaunchedEffect(shouldHide) {
		hidden = if (shouldHide) {
			delay(KEYBOARD_SETTLE_MS)
			true
		} else {
			false
		}
	}
	if (!hidden) {
		Box(modifier) {
			content()
		}
	}
}
