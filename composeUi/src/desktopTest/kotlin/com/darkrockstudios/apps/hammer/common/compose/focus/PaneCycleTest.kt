package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.actions.ProvideActions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PaneCycleTest {

	private val f6 = KeyChord.bare(ChordKey.F6)
	private val shiftF6 = KeyChord.bare(ChordKey.F6, shift = true)
	private val registry = ActionRegistry()
	private var paneFocus: PaneFocusState? = null

	@Composable
	private fun Stop(tag: String) {
		Box(Modifier.size(20.dp).testTag(tag).focusable())
	}

	private fun ComposeUiTest.showPanes(
		detail: @Composable () -> Unit = { Stop("b") },
		over: @Composable () -> Unit = {},
	) {
		setContent {
			ProvideActions(registry) {
				ProvidePaneFocus {
					paneFocus = LocalPaneFocus.current
					Row {
						Box(Modifier.size(100.dp).focusPane(PaneId.List)) {
							Column {
								Stop("a1")
								Stop("a2")
							}
						}
						Box(Modifier.size(100.dp).focusPane(PaneId.Detail)) { detail() }
					}
					over()
				}
			}
		}
		waitForIdle()
	}

	private fun ComposeUiTest.press(chord: KeyChord): Boolean {
		val handled = runOnIdle { registry.dispatch(chord) }
		waitForIdle()
		return handled
	}

	@Test
	fun `F6 moves focus to the next pane and wraps`() = runComposeUiTest {
		showPanes()

		press(f6)
		onNodeWithTag("a1").assertIsFocused()
		press(f6)
		onNodeWithTag("b").assertIsFocused()
		press(f6)

		onNodeWithTag("a1").assertIsFocused()
		assertEquals(PaneId.List, paneFocus?.focusedPane)
	}

	@Test
	fun `Shift+F6 goes back`() = runComposeUiTest {
		showPanes()

		press(shiftF6)
		onNodeWithTag("b").assertIsFocused()
		press(shiftF6)

		onNodeWithTag("a1").assertIsFocused()
	}

	@Test
	fun `a pane gets back the child it last had focused`() = runComposeUiTest {
		showPanes()
		onNodeWithTag("a2").requestFocus()
		waitForIdle()

		press(f6)
		onNodeWithTag("b").assertIsFocused()
		press(f6)

		onNodeWithTag("a2").assertIsFocused()
	}

	@Test
	fun `a pane with nothing focusable still takes focus`() = runComposeUiTest {
		showPanes(detail = {})

		press(f6)
		press(f6)

		assertEquals(PaneId.Detail, paneFocus?.focusedPane)
		assertTrue(paneFocus?.isContainerFocused(PaneId.Detail) == true)
		onNodeWithTag("a1").assertIsNotFocused()
	}

	@Test
	fun `F6 is not live with no panes`() = runComposeUiTest {
		setContent {
			ProvideActions(registry) {
				ProvidePaneFocus {
					Stop("loose")
				}
			}
		}
		waitForIdle()

		val live = runOnIdle { registry.isLive(HammerActions.PaneNext.id) }
		val handled = press(f6)

		assertFalse(live)
		assertFalse(handled)
	}

	@Test
	fun `F6 is blocked while a component overlay such as focus mode is up`() = runComposeUiTest {
		showPanes()
		// Focus mode and the outline hold a barrier like this one from their components.
		runOnIdle { registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true) }

		val handled = press(f6)

		assertFalse(handled)
		assertNull(paneFocus?.focusedPane)
	}

	@Test
	fun `F6 is blocked while a dialog is open`() = runComposeUiTest {
		showPanes(over = { AnimatedDialog(visible = true, onCloseRequest = {}) {} })

		val handled = press(shiftF6)

		assertFalse(handled)
		assertNull(paneFocus?.focusedPane)
	}
}
