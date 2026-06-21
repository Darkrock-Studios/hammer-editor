package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.projects.ProjectWithSyncDate
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import java.net.URLEncoder
import java.security.MessageDigest

object ProjectName {
	private const val SHORT_ID_LENGTH = 6
	private const val BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
	private val nonSlugRun = Regex("""[^\p{L}\p{N}]+""")

	/**
	 * Short, stable, per-project identifier derived from the project's uuid. This is the
	 * authoritative part of a project URL; the slug beside it is purely decorative. Six base62
	 * characters (~35 bits) is far more than enough to stay unique within a single user's project
	 * list — the only scope a URL is ever resolved against.
	 */
	fun shortId(projectUuid: String): String {
		val digest = MessageDigest.getInstance("SHA-256").digest(projectUuid.toByteArray(Charsets.UTF_8))
		var value = 0L
		for (i in 0 until 5) value = (value shl 8) or (digest[i].toLong() and 0xFF) // 40 bits
		val chars = CharArray(SHORT_ID_LENGTH)
		for (i in SHORT_ID_LENGTH - 1 downTo 0) {
			chars[i] = BASE62[(value % 62).toInt()]
			value /= 62
		}
		return String(chars)
	}

	/**
	 * A pretty, decorative slug for a name: runs of non-alphanumeric characters collapse to a
	 * single dash. Lossy by design — it is never parsed back, only shown.
	 */
	fun slug(name: String): String =
		name.trim().replace(nonSlugRun, "-").trim('-').ifEmpty { "project" }

	/**
	 * The full project URL path segment, `"{slug}-{shortId}"`, percent-encoded for transport. The
	 * leading slug is for humans; the trailing id is what [idFromSegment] reads back.
	 */
	fun projectSegment(name: String, projectUuid: String): String =
		URLEncoder.encode("${slug(name)}-${shortId(projectUuid)}", "UTF-8")

	/**
	 * Extract the project id from an incoming (already percent-decoded) path segment: everything
	 * after the last dash. A bare id with no slug (e.g. `/story/7f3k2a`) returns itself, so short
	 * URLs work too. The id contains no dashes, so the last dash is always the separator.
	 */
	fun idFromSegment(segment: String): String = segment.substringAfterLast('-', segment)

	/** Pen names stay human-readable handles in URLs: spaces become dashes, then percent-encode. */
	fun penNameForUrl(penName: String): String =
		URLEncoder.encode(penName.replace(' ', '-'), "UTF-8")

	/** Inverse used for pen-name lookups; the router has already percent-decoded the segment. */
	fun penNameFromUrl(segment: String): String = segment.replace('-', ' ')
}

/**
 * Resolve a project from its URL path segment by matching the embedded [ProjectName.shortId]
 * against the user's own projects.
 */
suspend fun ProjectsRepository.findProjectByUrlSegment(
	userId: Long,
	segment: String,
): ProjectWithSyncDate? {
	val id = ProjectName.idFromSegment(segment)
	return getProjectsWithSyncDate(userId).find { ProjectName.shortId(it.uuid) == id }
}

/**
 * Resolve something keyed by a pen name from a URL segment: try it verbatim, then with dashes
 * treated as spaces (the common "Jane Doe" -> "Jane-Doe" case).
 */
suspend fun <T : Any> resolveByPenName(segment: String, lookup: suspend (String) -> T?): T? {
	lookup(segment)?.let { return it }
	val spaced = ProjectName.penNameFromUrl(segment)
	return if (spaced != segment) lookup(spaced) else null
}
