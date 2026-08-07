package com.darkrockstudios.apps.hammer.common.compose

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectShortcutHostTest {

	@Test
	fun `unbound host reports the key as unhandled`() {
		val host = ProjectShortcutHost()

		assertFalse(host.startProjectSync())
		assertFalse(host.saveAllBuffers())
	}

	@Test
	fun `bound host runs the actions and claims the key`() {
		var syncs = 0
		var saves = 0
		val host = ProjectShortcutHost()
		host.bind(startSync = { syncs++ }, saveAll = { saves++ })

		assertTrue(host.startProjectSync())
		assertTrue(host.saveAllBuffers())
		assertEquals(1, syncs)
		assertEquals(1, saves)
	}

	@Test
	fun `unbinding stops the actions running`() {
		var syncs = 0
		val host = ProjectShortcutHost()
		host.bind(startSync = { syncs++ }, saveAll = {})
		host.unbind()

		assertFalse(host.startProjectSync())
		assertEquals(0, syncs)
	}
}
