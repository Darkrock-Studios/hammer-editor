package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.AnimatedFullScreenDialog
import com.darkrockstudios.apps.hammer.common.compose.TopAppBarDropdownMenu
import com.darkrockstudios.apps.hammer.common.projectroot.UnsavedEntityDiscardConfirm
import com.darkrockstudios.apps.hammer.common.projectroot.UnsavedScenesConfirm
import com.darkrockstudios.apps.hammer.unsaved_notes_dialog_message
import com.darkrockstudios.apps.hammer.Res
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DialogBarrierTest {

	private val modN = KeyChord.mod(ChordKey.N)
	private val modShiftO = KeyChord.mod(ChordKey.O, shift = true)

	private fun registryWithItemNew() = ActionRegistry().apply {
		register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to ActionHandler {}))
	}

	@Test
	fun `an open dialog blocks module chords`() = runComposeUiTest {
		val registry = registryWithItemNew()
		setContent {
			ProvideActions(registry) {
				AnimatedDialog(visible = true, onCloseRequest = {}) {}
			}
		}

		waitForIdle()

		assertFalse(registry.dispatch(modN))
	}

	@Test
	fun `closing the dialog restores them`() = runComposeUiTest {
		val registry = registryWithItemNew()
		var visible by mutableStateOf(true)
		setContent {
			ProvideActions(registry) {
				AnimatedDialog(visible = visible, onCloseRequest = { visible = false }) {}
			}
		}
		waitForIdle()

		visible = false
		waitForIdle()

		assertTrue(registry.dispatch(modN))
	}

	@Test
	fun `a dialog with blocksActions false does not block`() = runComposeUiTest {
		val registry = registryWithItemNew()
		setContent {
			ProvideActions(registry) {
				AnimatedDialog(visible = true, onCloseRequest = {}, blocksActions = false) {}
			}
		}

		waitForIdle()

		assertTrue(registry.dispatch(modN))
		assertFalse(registry.hasOverlay())
	}

	@Test
	fun `Esc dismisses an open full-screen dialog through the overlay`() = runComposeUiTest {
		val registry = registryWithItemNew()
		var dismissed = 0
		var shown by mutableStateOf(true)
		setContent {
			ProvideActions(registry) {
				if (shown) {
					AnimatedFullScreenDialog(onDismissed = { dismissed++; shown = false }) {}
				}
			}
		}
		waitForIdle()

		val wasDismissable = registry.dismissTopOverlay()
		waitForIdle()

		assertTrue(wasDismissable)
		assertEquals(1, dismissed)
		assertFalse(registry.hasOverlay())
		assertTrue(registry.dispatch(modN))
	}

	@Test
	fun `a full-screen dialog lets its component overlay's chords through`() = runComposeUiTest {
		val registry = registryWithItemNew()
		var toggles = 0
		registry.register(
			ActionLayer.Overlay,
			mapOf(HammerActions.StoryToggleOutline.id to ActionHandler { toggles++ }),
			blocksBelow = true,
		)
		setContent {
			ProvideActions(registry) {
				AnimatedFullScreenDialog(onDismissed = {}) {}
			}
		}
		waitForIdle()

		val toggleHandled = registry.dispatch(modShiftO)
		val newHandled = registry.dispatch(modN)

		assertTrue(toggleHandled)
		assertEquals(1, toggles)
		assertFalse(newHandled)
	}

	@Test
	fun `a closing dialog blocks until it is gone and ignores a second Esc`() = runComposeUiTest {
		val registry = registryWithItemNew()
		var visible by mutableStateOf(true)
		var closeRequests = 0
		setContent {
			ProvideActions(registry) {
				AnimatedDialog(visible = visible, onCloseRequest = { closeRequests++; visible = false }) {}
			}
		}
		waitForIdle()

		mainClock.autoAdvance = false
		registry.dismissTopOverlay()
		mainClock.advanceTimeByFrame()
		mainClock.advanceTimeBy(50)
		val blockedWhileClosing = !registry.dispatch(modN)
		val secondEscTaken = registry.dismissTopOverlay()
		mainClock.autoAdvance = true
		waitForIdle()

		assertTrue(blockedWhileClosing)
		assertTrue(secondEscTaken)
		assertEquals(1, closeRequests)
		assertFalse(registry.hasOverlay())
		assertTrue(registry.dispatch(modN))
	}

	@Test
	fun `Esc on the unsaved scenes confirm cancels`() = runComposeUiTest {
		val registry = ActionRegistry()
		val chosen = mutableListOf<String>()
		setContent {
			ProvideActions(registry) {
				UnsavedScenesConfirm(
					onSave = { chosen += "save" },
					onDiscard = { chosen += "discard" },
					onCancel = { chosen += "cancel" },
				)
			}
		}
		waitForIdle()

		val dismissed = registry.dismissTopOverlay()

		assertTrue(dismissed)
		assertEquals(listOf("cancel"), chosen)
	}

	@Test
	fun `Esc on the unsaved entity confirm keeps editing`() = runComposeUiTest {
		val registry = ActionRegistry()
		val chosen = mutableListOf<String>()
		setContent {
			ProvideActions(registry) {
				UnsavedEntityDiscardConfirm(
					message = Res.string.unsaved_notes_dialog_message,
					onDiscard = { chosen += "discard" },
					onKeep = { chosen += "keep" },
				)
			}
		}
		waitForIdle()

		val dismissed = registry.dismissTopOverlay()

		assertTrue(dismissed)
		assertEquals(listOf("keep"), chosen)
	}

	@Test
	fun `Esc closes a menu open in a dialog before the dialog`() = runComposeUiTest {
		val registry = ActionRegistry()
		var dialogCloses = 0
		setContent {
			ProvideActions(registry) {
				AnimatedDialog(visible = true, onCloseRequest = { dialogCloses++ }) {
					TopAppBarDropdownMenu(menuItems = emptySet())
				}
			}
		}
		waitForIdle()
		onNode(hasClickAction()).performClick()
		waitForIdle()

		registry.dismissTopOverlay()
		waitForIdle()
		val closesAfterFirstEsc = dialogCloses
		registry.dismissTopOverlay()

		assertEquals(0, closesAfterFirstEsc)
		assertEquals(1, dialogCloses)
	}
}
