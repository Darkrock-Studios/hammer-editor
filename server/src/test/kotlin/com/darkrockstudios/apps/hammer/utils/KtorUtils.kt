package com.darkrockstudios.apps.hammer.utils

import io.ktor.server.application.Application
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.PathSegmentParameterRouteSelector
import io.ktor.server.routing.PathSegmentWildcardRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing

/**
 * Collects all the routes defined in the application.
 *
 * @return A list of strings representing the registered routes.
 */
fun Application.collectRoutes(): List<String> {
	return routing {}.allRoutes()
}

/**
 * Recursively collects all the terminal routes from the current route and its children.
 * Terminal routes are those that directly handle requests and are not just path prefixes.
 *
 * @param prefix The prefix to be used for the path of each route.
 *               It accumulates as it goes deeper into the route tree.
 * @return A list of strings representing the terminal routes.
 */
private fun Route.allRoutes(prefix: String = ""): List<String> {
	val routes = mutableListOf<String>()

	/**
	 * Checks if the route is a prefix (non-terminal) route.
	 * A prefix route is identified by having children with specific HTTP method selectors.
	 *
	 * @return True if the route is a prefix route, false otherwise.
	 */
	fun Route.isPrefixRoute(): Boolean {
		return this.children.any { it.selector is HttpMethodRouteSelector }
	}

	/**
	 * Recursive function to traverse the route tree and collect terminal routes.
	 *
	 * @param currentPath The current path accumulated from the parent routes.
	 */
	fun Route.collectRoutes(currentPath: String) {
		val pathSegment = when (val selector = this.selector) {
			is PathSegmentConstantRouteSelector -> "${currentPath}/${selector.value}"
			is PathSegmentParameterRouteSelector -> "${currentPath}/{${selector.name}}"
			is PathSegmentWildcardRouteSelector -> "${currentPath}/*"
			else -> currentPath
		}

		// Add the route if it's a terminal route (has an HttpMethodRouteSelector and is not a prefix route).
		if (this.selector is HttpMethodRouteSelector && !this.isPrefixRoute()) {
			val method = (this.selector as HttpMethodRouteSelector).method.value
			routes.add("$method $pathSegment")
		}

		// Recursively collect routes from children.
		this.children.forEach { it.collectRoutes(pathSegment) }
	}

	this.collectRoutes(prefix)

	return routes.distinct()
}