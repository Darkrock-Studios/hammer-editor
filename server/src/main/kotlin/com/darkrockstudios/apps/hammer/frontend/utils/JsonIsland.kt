package com.darkrockstudios.apps.hammer.frontend.utils

import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

private val islandJson = Json { encodeDefaults = true }

/**
 * JSON destined for an inline `<script type="application/json">` island. Escapes `<` so content
 * containing `</script>` can't break out of the element.
 *
 * Such an island is inert data, not code, so it stays available to page scripts under a CSP with
 * no `'unsafe-inline'` — the route by which server-rendered values reach client JS.
 */
fun jsonIsland(json: String): String = json.replace("<", "\\u003c")

/**
 * Localized UI strings as a JSON island, keyed by the name the client uses.
 *
 * [keys] maps each client-side name to its message-bundle key. Values are JSON-escaped, which
 * plain Mustache interpolation into a script body is not.
 */
suspend fun ApplicationCall.messagesIsland(keys: Map<String, String>): String {
	val strings = keys.mapValues { (_, messageKey) -> msg(messageKey) }
	return jsonIsland(
		islandJson.encodeToString(MapSerializer(serializer<String>(), serializer<String>()), strings)
	)
}
