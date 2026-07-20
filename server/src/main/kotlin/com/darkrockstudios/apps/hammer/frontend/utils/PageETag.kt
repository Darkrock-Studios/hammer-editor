package com.darkrockstudios.apps.hammer.frontend.utils

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import java.security.MessageDigest

/**
 * A validator for a server-rendered page: the SHA-256 of its Mustache model plus any [extra] inputs
 * that shape the response without living in the model.
 *
 * Hashing the model itself — rather than an enumerated list of inputs — means a field added to a
 * page automatically joins its validator, instead of quietly serving a stale page to everyone
 * holding the old one. `msg` and `locales` are skipped: they are bulky and fully determined by the
 * `locale` and `version` entries, which are hashed.
 *
 * Weak, because Compression may re-encode the body and a strong validator promises byte equality.
 */
fun pageETag(model: Map<String, Any>, vararg extra: Any?): String {
	val digest = MessageDigest.getInstance("SHA-256")
	model.entries
		.filter { it.key !in UNHASHED_MODEL_KEYS }
		.sortedBy { it.key }
		.forEach { digest.update("${it.key}=${it.value}\n".toByteArray(Charsets.UTF_8)) }
	extra.forEach { digest.update("$it\n".toByteArray(Charsets.UTF_8)) }

	val hex = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
	return "W/\"$hex\""
}

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

private val UNHASHED_MODEL_KEYS = setOf("msg", "locales")
