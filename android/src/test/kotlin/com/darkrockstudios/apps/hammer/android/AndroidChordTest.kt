package com.darkrockstudios.apps.hammer.android

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_F3
import android.view.KeyEvent.KEYCODE_N
import android.view.KeyEvent.KEYCODE_NUMPAD_3
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import android.view.KeyEvent.KEYCODE_Q
import android.view.KeyEvent.KEYCODE_S
import android.view.KeyEvent.KEYCODE_TAB
import android.view.KeyEvent.META_ALT_ON
import android.view.KeyEvent.META_ALT_RIGHT_ON
import android.view.KeyEvent.META_CAPS_LOCK_ON
import android.view.KeyEvent.META_CTRL_LEFT_ON
import android.view.KeyEvent.META_CTRL_ON
import android.view.KeyEvent.META_META_ON
import android.view.KeyEvent.META_NUM_LOCK_ON
import android.view.KeyEvent.META_SHIFT_ON
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidChordTest {

	@Test
	fun `ctrl N is Mod+N`() =
		assertEquals(KeyChord.mod(ChordKey.N), androidChord(KEYCODE_N, META_CTRL_ON or META_CTRL_LEFT_ON))

	@Test
	fun `ctrl shift S is save all`() =
		assertEquals(KeyChord.mod(ChordKey.S, shift = true), androidChord(KEYCODE_S, META_CTRL_ON or META_SHIFT_ON))

	@Test
	fun `bare F3 is the sync chord`() = assertEquals(KeyChord.bare(ChordKey.F3), androidChord(KEYCODE_F3, 0))

	@Test
	fun `alt chords are not actions`() {
		assertNull(androidChord(KEYCODE_Q, META_CTRL_ON or META_ALT_ON or META_ALT_RIGHT_ON))
		assertNull(androidChord(KEYCODE_S, META_CTRL_ON or META_SHIFT_ON or META_ALT_ON))
	}

	@Test
	fun `meta chords are not actions`() = assertNull(androidChord(KEYCODE_N, META_META_ON))

	@Test
	fun `lock keys do not block chords`() = assertEquals(
		KeyChord.mod(ChordKey.Digit3),
		androidChord(KEYCODE_NUMPAD_3, META_CTRL_ON or META_NUM_LOCK_ON or META_CAPS_LOCK_ON),
	)

	@Test
	fun `numpad enter is enter`() = assertEquals(
		KeyChord.mod(ChordKey.Enter, shift = true),
		androidChord(KEYCODE_NUMPAD_ENTER, META_CTRL_ON or META_SHIFT_ON),
	)

	@Test
	fun `keys outside the table are not chords`() = assertNull(androidChord(KEYCODE_TAB, META_CTRL_ON))

	@Test
	fun `F3 on a project without sync is not consumed`() {
		val actions = ActionRegistry()
		actions.register(
			ActionLayer.Root,
			mapOf(HammerActions.ProjectSync.id to ActionHandler(isEnabled = { false }) {}),
		)

		assertFalse(dispatchProjectKey(actions, ACTION_DOWN, KEYCODE_F3, 0))
	}

	@Test
	fun `F3 on a project with sync is consumed`() {
		val actions = ActionRegistry()
		var syncs = 0
		actions.register(ActionLayer.Root, mapOf(HammerActions.ProjectSync.id to ActionHandler { syncs++ }))

		val consumed = dispatchProjectKey(actions, ACTION_DOWN, KEYCODE_F3, 0)

		assertTrue(consumed)
		assertEquals(1, syncs)
	}

	@Test
	fun `a key release is never consumed`() {
		val actions = ActionRegistry()
		var syncs = 0
		actions.register(ActionLayer.Root, mapOf(HammerActions.ProjectSync.id to ActionHandler { syncs++ }))

		val consumed = dispatchProjectKey(actions, ACTION_UP, KEYCODE_F3, 0)

		assertFalse(consumed)
		assertEquals(0, syncs)
	}

	@Test
	fun `no project means no key is consumed`() =
		assertFalse(dispatchProjectKey(null, ACTION_DOWN, KEYCODE_F3, 0))
}
