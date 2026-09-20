package com.darkrockstudios.apps.hammer.wear.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException

class LocalNetworkAddressTest {

	/** Literal addresses never touch DNS, so these run offline. */
	private fun literal(host: String): List<InetAddress> = listOf(InetAddress.getByName(host))

	private fun unresolvable(@Suppress("UNUSED_PARAMETER") host: String): List<InetAddress> =
		throw UnknownHostException(host)

	@Test
	fun `the port is not part of the host`() {
		assertEquals("192.168.1.46", hostOf("192.168.1.46:8081"))
		assertEquals("nas.lan", hostOf("nas.lan"))
		assertEquals("fd00::1", hostOf("[fd00::1]:8080"))
		assertEquals("fe80::1", hostOf("fe80::1"))
	}

	@Test
	fun `home network ranges need local network access`() {
		listOf("192.168.1.46:8081", "10.0.0.5", "172.20.1.1:8080", "127.0.0.1:8443").forEach { address ->
			assertTrue(isLocalNetworkServer(address, ::literal), address)
		}
	}

	@Test
	fun `a public address does not`() {
		listOf("93.184.216.34", "8.8.8.8:443", "172.32.0.1").forEach { address ->
			assertFalse(isLocalNetworkServer(address, ::literal), address)
		}
	}

	@Test
	fun `a mesh VPN address counts as local`() {
		// Tailscale hands out the carrier-grade NAT range.
		assertTrue(isLocalNetworkServer("100.101.102.103:8080", ::literal))
		assertFalse(isLocalNetworkServer("100.128.0.1", ::literal))
	}

	@Test
	fun `IPv6 unique local and link-local addresses count as local`() {
		assertTrue(isLocalNetworkServer("[fd12:3456::1]:8080", ::literal))
		assertTrue(isLocalNetworkServer("[fe80::1]:8080", ::literal))
		assertFalse(isLocalNetworkServer("[2606:4700::1111]:443", ::literal))
	}

	@Test
	fun `a name is judged by what it resolves to`() {
		val resolvesToLan = { _: String -> literal("192.168.1.46") }
		val resolvesToPublic = { _: String -> literal("93.184.216.34") }

		// A public-looking name with a LAN record is exactly the case that looking alone misses.
		assertTrue(isLocalNetworkServer("hammer.example.com", resolvesToLan))
		assertFalse(isLocalNetworkServer("hammer.ink", resolvesToPublic))
	}

	@Test
	fun `an unresolvable name falls back to how it looks`() {
		assertTrue(isLocalNetworkServer("nas.local:8080", ::unresolvable))
		assertTrue(isLocalNetworkServer("server.lan", ::unresolvable))
		assertTrue(isLocalNetworkServer("mynas", ::unresolvable))
		assertTrue(isLocalNetworkServer("localhost:8080", ::unresolvable))
		assertFalse(isLocalNetworkServer("hammer.ink", ::unresolvable))
	}

	@Test
	fun `an empty address is not local`() {
		assertFalse(isLocalNetworkServer("", ::unresolvable))
	}
}
