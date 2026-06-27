package com.darkrockstudios.apps.hammer.monitoring

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*

/** The cleaned matched-route template, stashed on the call during routing. */
val MatchedRouteTemplateKey = AttributeKey<String>("MatchedRouteTemplate")

/** Strips Ktor's non-path route selectors — (method:POST), (authenticate ...), etc. */
private val SELECTOR_NOISE = Regex("""/\([^)]*\)""")

/**
 * The matched route TEMPLATE (e.g. `/api/project/{userId}/{projectId}/upload_entity/{entityId}`),
 * with Ktor's method/auth selector noise removed. Keyed this way so per-user /
 * per-project path parameters don't explode cardinality, and so error rows and
 * metric buckets agree on the exact same string for a given endpoint.
 */
fun routeTemplate(route: RoutingNode): String = route.toString().replace(SELECTOR_NOISE, "")

/**
 * Stashes the cleaned route template onto each call as soon as routing resolves it
 * (before the handler runs), so consumers that fire *after* a handler throws — like
 * the StatusPages error recorder — can still read the template instead of falling
 * back to the concrete, user-data-bearing path.
 */
fun Application.configureRouteTemplateCapture() {
	monitor.subscribe(RoutingRoot.Plugin.RoutingCallStarted) { call ->
		call.attributes.put(MatchedRouteTemplateKey, routeTemplate(call.route))
	}
}
