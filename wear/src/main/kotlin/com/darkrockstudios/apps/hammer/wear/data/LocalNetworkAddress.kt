package com.darkrockstudios.apps.hammer.wear.data

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/** Names that only ever resolve on a home or office network. */
private val LOCAL_NAME_SUFFIXES = listOf(".local", ".lan", ".home", ".internal", ".home.arpa")

/**
 * The host part of a stored server address, which carries its port: `192.168.1.5:8080`,
 * `nas.lan`, `[fd00::1]:8080`.
 */
internal fun hostOf(serverAddress: String): String {
	val address = serverAddress.trim().substringBefore('/')
	return when {
		address.startsWith("[") -> address.substringAfter('[').substringBefore(']')
		// More than one colon without brackets is a bare IPv6 literal, not host:port.
		address.count { it == ':' } == 1 -> address.substringBefore(':')
		else -> address
	}
}

/**
 * Whether [serverAddress] points at the local network, and so needs Android 17's local network
 * permission to reach. Names are resolved, because `nas.lan` and a hostname with a LAN record look
 * no different from a public one. A name that cannot be resolved falls back to how it looks.
 */
fun isLocalNetworkServer(
	serverAddress: String,
	resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
): Boolean {
	val host = hostOf(serverAddress).lowercase()
	if (host.isEmpty()) return false

	val addresses = try {
		resolve(host)
	} catch (_: UnknownHostException) {
		return looksLocal(host)
	} catch (_: SecurityException) {
		return looksLocal(host)
	}
	return addresses.any { it.isLocalNetwork() }
}

private fun looksLocal(host: String): Boolean =
	host == "localhost" || !host.contains('.') || LOCAL_NAME_SUFFIXES.any { host.endsWith(it) }

/**
 * Private, loopback and link-local ranges, IPv6 unique local addresses, and the carrier-grade NAT
 * range that mesh VPNs such as Tailscale hand out. Over-including costs one prompt; missing one
 * leaves every sync timing out with no explanation.
 */
internal fun InetAddress.isLocalNetwork(): Boolean {
	if (isLoopbackAddress || isSiteLocalAddress || isLinkLocalAddress || isAnyLocalAddress) return true
	val bytes = address
	return when (this) {
		is Inet6Address -> (bytes[0].toInt() and 0xFE) == 0xFC
		is Inet4Address -> (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xC0) == 64
		else -> false
	}
}
