package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.projects.ProjectWithSyncDate
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import java.net.URLEncoder

object ProjectName {
	/**
	 * Encode a name as a single URL path segment. Lossless: standard percent-encoding, which the
	 * routing engine decodes back to the exact original before a handler reads `call.parameters`.
	 * Spaces become `%20` rather than `+` (a literal plus inside a path segment).
	 *
	 * Replaces the previous "spaces to dashes" slug, which collided with literal dashes (and any
	 * percent-encoded character) and so could not round-trip — names containing a dash 404'd. Old
	 * dash-slug links are still resolved via [legacyUrlNameToName].
	 */
	fun formatForUrl(name: String): String =
		URLEncoder.encode(name, "UTF-8").replace("+", "%20")

	/**
	 * Interpret a URL segment the way the old slug scheme did: every dash becomes a space. Lossy,
	 * kept only so links created before the encoding was made lossless still resolve.
	 */
	fun legacyUrlNameToName(urlSegment: String): String =
		urlSegment.replace('-', ' ')
}

/**
 * Resolve an entity addressed by an (already percent-decoded) URL segment, tolerating the legacy
 * dash-for-space slug. Tries the exact name first, then the legacy interpretation.
 */
suspend fun <T : Any> resolveByUrlName(urlName: String, lookup: suspend (String) -> T?): T? {
	lookup(urlName)?.let { return it }
	val legacy = ProjectName.legacyUrlNameToName(urlName)
	return if (legacy != urlName) lookup(legacy) else null
}

/**
 * Resolves a project from a URL segment. With the lossless encoding the segment the router hands
 * us is the exact project name, so the common case is a single exact lookup; the legacy fallback
 * only runs for dash-slug links created before the fix.
 */
suspend fun ProjectsRepository.findProjectByUrlName(
	userId: Long,
	urlName: String,
): ProjectWithSyncDate? = resolveByUrlName(urlName) { getProjectByName(userId, it) }
