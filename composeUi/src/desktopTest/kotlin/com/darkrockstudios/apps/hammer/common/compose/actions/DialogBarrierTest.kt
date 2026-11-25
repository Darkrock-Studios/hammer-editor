package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.AnimatedFullScreenDialog
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DialogBarrierTest {

	private val modN = KeyChord.mod(ChordKey.N)

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
		setContent {
			ProvideActions(registry) {
				AnimatedFullScreenDialog(onDismissed = { dismissed++ }) {}
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
}
