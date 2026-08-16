package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class MatchesShortcutTest {

	private fun shortcutFirings(
		shortcut: Modifier.(onFired: () -> Unit) -> Modifier,
		press: androidx.compose.ui.test.KeyInjectionScope.() -> Unit,
	): Int {
		var fired = 0
		runComposeUiTest {
			setContent {
				val focusRequester = remember { FocusRequester() }
				Box(
					Modifier
						.testTag("root")
						.focusRequester(focusRequester)
						.focusable()
						.shortcut { fired++ }
				)
				LaunchedEffect(Unit) { focusRequester.requestFocus() }
			}
			onNodeWithTag("root").performKeyInput(press)
		}
		return fired
	}

	@Test
	fun `bare F3 fires the sync shortcut`() {
		assertEquals(1, shortcutFirings({ onKeyShortcut(Key.F3, action = it) }) { pressKey(Key.F3) })
	}

	@Test
	fun `F3 with an extra modifier does not fire`() {
		assertEquals(
			0,
			shortcutFirings({ onKeyShortcut(Key.F3, action = it) }) {
				withKeyDown(Key.CtrlLeft) { pressKey(Key.F3) }
			},
		)
		assertEquals(
			0,
			shortcutFirings({ onKeyShortcut(Key.F3, action = it) }) {
				withKeyDown(Key.ShiftLeft) { pressKey(Key.F3) }
			},
		)
	}

	@Test
	fun `ctrl shift S fires save all`() {
		assertEquals(
			1,
			shortcutFirings({ onKeyShortcut(Key.S, ctrl = true, shift = true, action = it) }) {
				withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.S) } }
			},
		)
	}

	@Test
	fun `AltGr shift S does not fire save all`() {
		assertEquals(
			0,
			shortcutFirings({ onKeyShortcut(Key.S, ctrl = true, shift = true, action = it) }) {
				withKeyDown(Key.CtrlLeft) {
					withKeyDown(Key.AltLeft) {
						withKeyDown(Key.ShiftLeft) { pressKey(Key.S) }
					}
				}
			},
		)
	}
}
