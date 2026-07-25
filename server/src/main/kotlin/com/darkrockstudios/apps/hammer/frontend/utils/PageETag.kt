package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.utilities.sha256Hex
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond

/**
 * A validator for a server-rendered page: the SHA-256 of its Mustache model plus any [extra] inputs
 * that shape the response without living in the model.
 *
 * Hashing the model itself — rather than an enumerated list of inputs — means a field added to a
 * page automatically joins its validator, instead of quietly serving a stale page to everyone
 * holding the old one. `msg` and `locales` are skipped: they are bulky and fully determined by the
 * `locale` and `version` entries, which are hashed. A message formatted at request time is the one
 * exception, so [FORMATTED_MSG_KEY] mirrors those back into the hash.
 *
 * Weak, because Compression may re-encode the body and a strong validator promises byte equality.
 */
fun pageETag(model: Map<String, Any>, vararg extra: Any?): String {
	// Length-prefixed so a value containing the delimiters can't imitate another entry.
	val canonical = buildString {
		model.entries
			.filter { it.key !in UNHASHED_MODEL_KEYS }
			.sortedBy { it.key }
			.forEach { append(it.key).append('=').appendLengthPrefixed(it.value.toString()) }
		extra.forEach { appendLengthPrefixed(it.toString()) }
	}
	return "W/\"${sha256Hex(canonical)}\""
}

private fun StringBuilder.appendLengthPrefixed(value: String): StringBuilder =
	append(value.length).append(':').append(value).append('\n')

/** True when the client already holds [etag], so the caller can answer 304 without rendering. */
fun ApplicationCall.matchesETag(etag: String): Boolean =
	request.header(HttpHeaders.IfNoneMatch)
		?.split(',')
		?.any { val candidate = it.trim(); candidate == etag || candidate == "*" } == true

/**
 * Marks a page as always-revalidate and per-visitor: a browser may keep its copy but has to ask
 * before reusing it, and no shared cache may hand one visitor's page to another.
 */
fun ApplicationCall.applyRevalidationHeaders(etag: String) {
	response.header(HttpHeaders.ETag, etag)
	response.header(HttpHeaders.CacheControl, "private, no-cache")
	response.header(HttpHeaders.Vary, "Cookie, Accept-Language")
}

/**
 * Renders [template] with [model], or answers 304 when the caller already holds that exact page.
 *
 * Hashing happens here, once the model is complete, so every value that shapes the page is in the
 * validator by construction. This saves the Mustache render and the response body — not the work
 * that built the model. A page with a cheap fingerprint available *before* its expensive step
 * should hash early instead and skip that step too, the way the public story reader does.
 *
 * Anything that shapes the response without living in the model — a header such as `X-Robots-Tag` —
 * must be passed as [extra], or two different responses will share one validator.
 */
suspend fun ApplicationCall.respondPage(
	template: String,
	model: Map<String, Any>,
	vararg extra: Any?,
) {
	val etag = pageETag(model, *extra)
	applyRevalidationHeaders(etag)
	if (matchesETag(etag)) {
		respond(HttpStatusCode.NotModified)
	} else {
		respond(MustacheContent(template, model))
	}
}

private val UNHASHED_MODEL_KEYS = setOf("msg", "locales")
