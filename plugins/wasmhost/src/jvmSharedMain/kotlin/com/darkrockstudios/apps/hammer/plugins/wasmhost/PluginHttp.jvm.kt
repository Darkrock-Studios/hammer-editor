package com.darkrockstudios.apps.hammer.plugins.wasmhost

import java.net.HttpURLConnection
import java.net.URI

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000
private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

// HttpURLConnection, not java.net.http, which Android lacks. A redirect is returned to the plugin rather
// than followed, since it could lead to a host the plugin may not reach.
internal actual val platformPluginHttp: PluginHttp? = PluginHttp { request ->
	val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
	try {
		connection.instanceFollowRedirects = false
		connection.connectTimeout = CONNECT_TIMEOUT_MS
		connection.readTimeout = READ_TIMEOUT_MS
		connection.requestMethod = request.method.uppercase()
		request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
		request.body?.let { body ->
			connection.doOutput = true
			connection.setFixedLengthStreamingMode(body.size)
			connection.outputStream.use { it.write(body) }
		}
		val status = connection.responseCode
		val stream = if (status >= 400) connection.errorStream else connection.inputStream
		val body = stream?.use { input ->
			val bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1)
			if (bytes.size > MAX_RESPONSE_BYTES) error("Response from ${request.url} is over ${MAX_RESPONSE_BYTES / 1024 / 1024} MiB")
			bytes
		} ?: ByteArray(0)
		PluginHttpResponse(status, body)
	} finally {
		connection.disconnect()
	}
}
