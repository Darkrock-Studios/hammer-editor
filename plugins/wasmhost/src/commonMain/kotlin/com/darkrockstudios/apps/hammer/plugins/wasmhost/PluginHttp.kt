package com.darkrockstudios.apps.hammer.plugins.wasmhost

class PluginHttpRequest(
	val url: String,
	val method: String,
	val headers: Map<String, String>,
	val body: ByteArray?,
)

class PluginHttpResponse(val status: Int, val body: ByteArray)

/** Sends a plugin's request, through Extism's `http_request`. Throws when there is no response. */
fun interface PluginHttp {
	fun send(request: PluginHttpRequest): PluginHttpResponse
}

/** The platform's client, or null where plugins have no HTTP. */
internal expect val platformPluginHttp: PluginHttp?

/**
 * Why a plugin may not request [url], or null when it may: [declared] hosts, from its manifest, only over
 * `https`, and the [entered] host, from a setting, over `http` too, since a server of the user's own,
 * such as one on their network, often has no certificate.
 */
fun refusal(url: String, declared: List<String>, entered: String?): String? {
	val host = httpHost(url) ?: return "Only http and https URLs may be requested"
	return when {
		host == entered -> null
		host !in declared -> "$host is not a host this plugin may reach"
		!url.trim().startsWith("https://", ignoreCase = true) -> "$host may only be reached over https"
		else -> null
	}
}

/**
 * The host of an `http` or `https` URL, in lower case and without its port, or null for any other URL,
 * or one with user info, which could disguise its host.
 */
fun httpHost(url: String): String? {
	val rest = url.trim().let { text ->
		listOf("http://", "https://").firstOrNull { text.startsWith(it, ignoreCase = true) }?.let { text.substring(it.length) }
	} ?: return null
	val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
	if ('@' in authority) return null
	val host = if (authority.startsWith('[')) authority.substringBefore(']') + "]" else authority.substringBefore(':')
	return host.lowercase().takeIf { it.isNotEmpty() }
}
