package com.darkrockstudios.apps.hammer.monitoring

import kotlinx.serialization.Serializable

/**
 * Admin-defined rule marking an error group as noise. Matching errors are still
 * recorded and browsable, but are filed into the ignored drawer of the errors
 * panel and never emailed. A rule matches on exact exception type, optionally
 * narrowed by [routeGlob] where `*` matches any run of characters; a rule with
 * no glob ignores the type on every route.
 */
@Serializable
data class IgnoredErrorRule(
	val exceptionType: String,
	val routeGlob: String? = null,
) {
	private val routeRegex: Regex? by lazy {
		routeGlob?.split("*")?.joinToString(".*") { Regex.escape(it) }?.toRegex()
	}

	fun matches(exceptionType: String, route: String?): Boolean {
		if (exceptionType != this.exceptionType) return false
		val regex = routeRegex ?: return true
		return route != null && regex.matches(route)
	}
}

fun List<IgnoredErrorRule>.ignores(exceptionType: String, route: String?): Boolean =
	any { it.matches(exceptionType, route) }
