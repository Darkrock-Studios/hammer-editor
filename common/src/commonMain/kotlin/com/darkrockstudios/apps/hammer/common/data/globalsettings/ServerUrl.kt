package com.darkrockstudios.apps.hammer.common.data.globalsettings

/** A server address as typed by the user, split into the host and the scheme it implies. */
data class ParsedServerUrl(
	val host: String,
	val ssl: Boolean,
)

/**
 * Sync is HTTPS unless the user explicitly types an `http://` URL. Self-hosted servers on a LAN or
 * VPN often have no certificate, so plaintext is allowed, but it is never the default and never
 * inferred: a bare host or an `https://` URL always stays encrypted.
 */
fun parseServerUrl(url: String): ParsedServerUrl {
	val trimmed = url.trim()
	val insecure = trimmed.startsWith(HTTP_SCHEME, ignoreCase = true)

	val host = trimmed
		.lowercase()
		.removePrefix(HTTP_SCHEME)
		.removePrefix(HTTPS_SCHEME)
		.removeSuffix("/")

	return ParsedServerUrl(host = host, ssl = !insecure)
}

/** True when [url] would connect in the clear, for warning the user before they commit to it. */
fun isInsecureServerUrl(url: String): Boolean = parseServerUrl(url).ssl.not()

private const val HTTP_SCHEME = "http://"
private const val HTTPS_SCHEME = "https://"
