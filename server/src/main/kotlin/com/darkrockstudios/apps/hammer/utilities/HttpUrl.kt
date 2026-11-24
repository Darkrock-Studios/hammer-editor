package com.darkrockstudios.apps.hammer.utilities

import java.net.URI
import java.net.URISyntaxException

/** True for an absolute http(s) URL with a host. */
fun isHttpUrl(uri: URI): Boolean = uri.scheme?.lowercase() in setOf("http", "https") && uri.host != null

/** True for an absolute http(s) URL with a host. */
fun isHttpUrl(value: String): Boolean = try {
	isHttpUrl(URI(value))
} catch (_: URISyntaxException) {
	false
}
