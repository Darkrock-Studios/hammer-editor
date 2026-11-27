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
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ActionLocalsTest {

	private val modN = KeyChord.mod(ChordKey.N)

	@Test
	fun `composed handlers answer until they leave the composition`() = runComposeUiTest {
		val registry = ActionRegistry()
		var shown by mutableStateOf(true)
		var runs = 0
		setContent {
			ProvideActions(registry) {
				if (shown) {
					RegisterActions(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to ActionHandler { runs++ }))
				}
			}
		}

		waitForIdle()
		assertTrue(registry.dispatch(modN))

		shown = false
		waitForIdle()
		assertFalse(registry.dispatch(modN))
		assertEquals(1, runs)
	}

	@Test
	fun `the latest handler runs after a recomposition`() = runComposeUiTest {
		val registry = ActionRegistry()
		var target by mutableStateOf("first")
		val ran = mutableListOf<String>()
		setContent {
			ProvideActions(registry) {
				val current = target
				RegisterActions(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to ActionHandler { ran += current }))
			}
		}

		waitForIdle()
		target = "second"
		waitForIdle()
		registry.dispatch(modN)

		assertEquals(listOf("second"), ran)
	}

	@Test
	fun `disabled registrations do not answer`() = runComposeUiTest {
		val registry = ActionRegistry()
		setContent {
			ProvideActions(registry) {
				RegisterActions(
					ActionLayer.Module,
					mapOf(HammerActions.ItemNew.id to ActionHandler {}),
					enabled = false,
				)
			}
		}

		waitForIdle()
		assertFalse(registry.isLive(HammerActions.ItemNew.id))
	}
}
