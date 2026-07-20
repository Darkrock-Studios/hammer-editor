package com.darkrockstudios.apps.hammer.frontend.utils

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path

/**
 * Absolute canonical URL for [path]. Prefers the configured [publicBaseUrl]; falls back to the
 * request's own scheme/host ([requestBaseUrl]). The Host fallback is safe here — a canonical (or
 * OG) tag only affects the page's own response, unlike a link emailed to a third party — so the
 * tag still renders when `publicUrl` is unset.
 */
fun ApplicationCall.canonicalUrl(path: String = request.path()): String =
	buildCanonicalUrl(publicBaseUrl() ?: requestBaseUrl(), path)

internal fun buildCanonicalUrl(base: String, path: String): String {
	val normalizedPath = if (path.startsWith("/")) path else "/$path"
	return base.trimEnd('/') + normalizedPath
}

/**
 * A plain-text `<meta name="description">` value from free-form [source] (e.g. an author bio):
 * whitespace collapsed, then truncated to [maxLength] on a word boundary with an ellipsis.
 * Returns null when [source] is null/blank so the tag is omitted rather than rendered empty.
 */
internal fun metaDescription(source: String?, maxLength: Int = 160): String? {
	val collapsed = source?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
	if (collapsed.isEmpty()) return null
	if (collapsed.length <= maxLength) return collapsed

	val truncated = collapsed.take(maxLength)
	val onWordBoundary = truncated.substringBeforeLast(' ', truncated).trimEnd()
	return "$onWordBoundary…"
}
