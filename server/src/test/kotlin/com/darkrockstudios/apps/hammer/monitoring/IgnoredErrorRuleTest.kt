package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IgnoredErrorRuleTest {

	@Test
	fun `type-only rule matches the type on any route`() {
		val rule = IgnoredErrorRule("UnsupportedProtocolVersionException")

		assertTrue(rule.matches("UnsupportedProtocolVersionException", "/api/.env"))
		assertTrue(rule.matches("UnsupportedProtocolVersionException", "/api/version"))
		assertTrue(rule.matches("UnsupportedProtocolVersionException", null))
	}

	@Test
	fun `rule does not match a different exception type`() {
		val rule = IgnoredErrorRule("UnsupportedProtocolVersionException")

		assertFalse(rule.matches("RuntimeException", "/api/.env"))
	}

	@Test
	fun `glob without wildcard requires an exact route match`() {
		val rule = IgnoredErrorRule("RuntimeException", "/api/version")

		assertTrue(rule.matches("RuntimeException", "/api/version"))
		assertFalse(rule.matches("RuntimeException", "/api/version/2"))
	}

	@Test
	fun `wildcard glob matches any run of characters`() {
		val rule = IgnoredErrorRule("RuntimeException", "/api/*")

		assertTrue(rule.matches("RuntimeException", "/api/.env"))
		assertTrue(rule.matches("RuntimeException", "/api/.env/wp-admin/js"))
		assertFalse(rule.matches("RuntimeException", "/web/dashboard"))
	}

	@Test
	fun `glob rule does not match an error without a route`() {
		val rule = IgnoredErrorRule("RuntimeException", "/api/*")

		assertFalse(rule.matches("RuntimeException", null))
	}

	@Test
	fun `regex special characters in the glob are treated literally`() {
		val rule = IgnoredErrorRule("RuntimeException", "/api/projects/{id}/begin_sync")

		assertTrue(rule.matches("RuntimeException", "/api/projects/{id}/begin_sync"))
		assertFalse(rule.matches("RuntimeException", "/api/projects/27/begin_sync"))
	}

	@Test
	fun `ignores checks a list of rules`() {
		val rules = listOf(
			IgnoredErrorRule("UnsupportedProtocolVersionException"),
			IgnoredErrorRule("RuntimeException", "/api/*"),
		)

		assertTrue(rules.ignores("UnsupportedProtocolVersionException", "/anything"))
		assertTrue(rules.ignores("RuntimeException", "/api/sync"))
		assertFalse(rules.ignores("RuntimeException", "/web/home"))
		assertFalse(emptyList<IgnoredErrorRule>().ignores("RuntimeException", "/api/sync"))
	}

	@Test
	fun `rules survive a config serialization round-trip`() {
		val key = AdminServerConfig.IGNORED_ERROR_RULES
		val rules = listOf(
			IgnoredErrorRule("UnsupportedProtocolVersionException"),
			IgnoredErrorRule("RuntimeException", "/api/*"),
		)

		assertEquals(rules, key.parse(key.serialize(rules)))
		assertEquals(emptyList(), key.parse(key.serialize(emptyList())))
	}
}
