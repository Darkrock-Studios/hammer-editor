package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.compose.actions.EscapeRouter
import com.darkrockstudios.apps.hammer.common.compose.actions.ProvideActions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val LIST = "list"
private const val FIELD = "field"
private const val DIALOG = "dialog"

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class EscLadderTest {

	private val registry = ActionRegistry()
	private var backs = 0
	private var dismissals = 0
	private val router = EscapeRouter(registry) {
		backs++
		true
	}
	private var paneFocus: PaneFocusState? = null
	private var inputModes: InputModeManager? = null

	private var listShown by mutableStateOf(true)
	private var dialogShown by mutableStateOf(false)

	private val press = KeyEvent(key = Key.Escape, type = KeyEventType.KeyDown)
	private val release = KeyEvent(key = Key.Escape, type = KeyEventType.KeyUp)

	/**
	 * A window as the desktop host builds it: the [EscapeRouter] on the outermost preview and key
	 * hooks, a list pane, and a detail pane holding a text field. [beside]: the list sits next to
	 * the detail, as Story's multi-pane layout; otherwise the detail is on its own.
	 */
	private fun ComposeUiTest.showLadder(beside: Boolean) {
		listShown = beside
		setContent {
			ProvideActions(registry) {
				ProvidePaneFocus {
					val panes = LocalPaneFocus.current!!
					paneFocus = panes
					inputModes = LocalInputModeManager.current
					Box(Modifier.onPreviewKeyEvent(router::onPreviewKeyEvent).onKeyEvent(router::onKeyEvent)) {
						Row {
							if (listShown) {
								Box(Modifier.size(100.dp).focusPane(PaneId.List)) {
									Box(Modifier.size(20.dp).testTag(LIST).paneEntry(PaneId.List).focusable())
								}
							}
							Box(
								Modifier.size(200.dp)
									.escapeToPane { if (beside) panes.escapeToList() else panes.escapeToContainer(PaneId.Detail) }
									.focusPane(PaneId.Detail)
							) {
								var text by remember { mutableStateOf("Once upon a time") }
								BasicTextField(text, { text = it }, Modifier.size(150.dp, 40.dp).testTag(FIELD))
							}
						}
						if (dialogShown) {
							Dialog(onDismissRequest = { dialogShown = false }) {
								val focus = remember { FocusRequester() }
								Box(
									Modifier.size(50.dp).testTag(DIALOG)
										.onKeyEvent { event ->
											val esc = event.key == Key.Escape && event.type == KeyEventType.KeyDown
											if (esc) dialogShown = false
											esc
										}
										.focusRequester(focus)
										.focusable()
								)
								LaunchedEffect(Unit) { focus.requestFocus() }
							}
						}
					}
				}
			}
		}
		waitForIdle()
	}

	private fun ComposeUiTest.focusField() {
		onNodeWithTag(FIELD).requestFocus()
		waitForIdle()
	}

	/** One Esc tap: the press and the release go to whatever the window has focused. */
	private fun ComposeUiTest.esc() {
		onNodeWithTag(FIELD).performKeyPress(press)
		waitForIdle()
		onNodeWithTag(FIELD).performKeyPress(release)
		waitForIdle()
	}

	@Test
	fun `an Esc release without its press does nothing`() = runComposeUiTest {
		showLadder(beside = true)
		focusField()

		onNodeWithTag(FIELD).performKeyPress(release)
		waitForIdle()

		onNodeWithTag(FIELD).assertIsFocused()
		assertEquals(0, backs)
	}

	@Test
	fun `Esc in the detail pane moves focus to the list and requests a reveal`() = runComposeUiTest {
		showLadder(beside = true)
		focusField()

		esc()

		onNodeWithTag(LIST).assertIsFocused()
		assertEquals(1, paneFocus!!.revealRequests(PaneId.List))
		assertEquals(0, backs)
	}

	@Test
	fun `Esc to the list puts the input mode back to keyboard`() = runComposeUiTest {
		showLadder(beside = true)
		onNodeWithTag(FIELD).performMouseInput { click() }
		waitForIdle()
		val modeAfterClick = runOnIdle { inputModes!!.inputMode }

		esc()

		assertEquals(InputMode.Touch, modeAfterClick, "A mouse click puts Compose in touch mode")
		assertEquals(InputMode.Keyboard, runOnIdle { inputModes!!.inputMode })
	}

	@Test
	fun `a second Esc goes back`() = runComposeUiTest {
		showLadder(beside = true)
		focusField()

		esc()
		esc()

		assertEquals(1, backs)
	}

	@Test
	fun `holding Esc takes one step`() = runComposeUiTest {
		showLadder(beside = true)
		focusField()

		onNodeWithTag(FIELD).performKeyPress(press)
		onNodeWithTag(FIELD).performKeyPress(press)
		onNodeWithTag(FIELD).performKeyPress(press)
		onNodeWithTag(FIELD).performKeyPress(release)
		waitForIdle()

		onNodeWithTag(LIST).assertIsFocused()
		assertEquals(0, backs)
	}

	@Test
	fun `single pane - the first Esc leaves the text field and keeps editing`() = runComposeUiTest {
		showLadder(beside = false)
		focusField()

		esc()

		onNodeWithTag(FIELD).assertExists().assertIsNotFocused()
		assertTrue(paneFocus!!.isContainerFocused(PaneId.Detail))
		assertEquals(0, backs)
	}

	@Test
	fun `single pane - the next Esc goes back`() = runComposeUiTest {
		showLadder(beside = false)
		focusField()

		esc()
		esc()

		assertEquals(1, backs)
	}

	@Test
	fun `a dialog dismissed by Esc does not also step back`() = runComposeUiTest {
		showLadder(beside = true)
		focusField()
		dialogShown = true
		waitForIdle()

		onNodeWithTag(DIALOG).performKeyPress(press)
		waitForIdle()
		focusField()
		onNodeWithTag(FIELD).performKeyPress(release)
		waitForIdle()

		assertFalse(dialogShown)
		onNodeWithTag(FIELD).assertIsFocused()
		assertEquals(0, paneFocus!!.revealRequests(PaneId.List))
		assertEquals(0, backs)
	}

	@Test
	fun `Esc with a menu open closes the menu and leaves the ladder alone`() = runComposeUiTest {
		showLadder(beside = true)
		focusField()
		registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true, onDismiss = { dismissals++ })

		esc()

		assertEquals(1, dismissals)
		onNodeWithTag(FIELD).assertIsFocused()
		assertEquals(0, backs)
	}

	@Test
	fun `closing the detail in keyboard mode hands focus to the list`() = runComposeUiTest {
		var detailOpen by mutableStateOf(true)
		setContent {
			ProvideActions(registry) {
				ProvidePaneFocus {
					paneFocus = LocalPaneFocus.current
					FocusFirstPaneOnArrival(detailOpen, enabled = !detailOpen)
					if (detailOpen) {
						Box(Modifier.size(200.dp).escapeToContainer(PaneId.Detail).focusPane(PaneId.Detail)) {
							BasicTextField("text", {}, Modifier.size(150.dp, 40.dp).testTag(FIELD))
						}
					} else {
						Box(Modifier.size(100.dp).focusPane(PaneId.List)) {
							Box(Modifier.size(20.dp).testTag(LIST).paneEntry(PaneId.List).focusable())
						}
					}
				}
			}
		}
		waitForIdle()
		focusField()
		onNodeWithTag(FIELD).performKeyPress(press)
		waitForIdle()

		detailOpen = false
		waitForIdle()

		onNodeWithTag(LIST).assertIsFocused()
	}
}
