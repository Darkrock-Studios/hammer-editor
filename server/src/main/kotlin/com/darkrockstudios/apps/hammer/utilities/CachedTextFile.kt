package com.darkrockstudios.apps.hammer.utilities

import okio.FileSystem
import okio.Path

/**
 * Reads an optional plaintext file, returning its contents or null when the path is unset, missing,
 * not a regular file, or blank. The result (including the "no text" outcome) is cached and only
 * re-read when the file's size or modification time changes, so repeated reads avoid disk work.
 *
 * A read failure on an existing file propagates; callers that must not disable a gated feature on a
 * transient IO error rely on that rather than a silently-null result.
 */
class CachedTextFile(
	private val path: Path?,
	private val fileSystem: FileSystem,
) {
	private var cached: Cached? = null

	@Synchronized
	fun read(): String? {
		val path = path ?: return null

		val metadata = fileSystem.metadataOrNull(path)
		val size = metadata?.size
		val lastModifiedAtMillis = metadata?.lastModifiedAtMillis

		val cached = cached
		if (cached != null && cached.matches(size, lastModifiedAtMillis)) {
			return cached.text
		}

		val text = if (metadata?.isRegularFile == true) {
			fileSystem.read(path) { readUtf8() }.takeUnless { it.isBlank() }
		} else {
			null
		}

		this.cached = Cached(size, lastModifiedAtMillis, text)
		return text
	}

	private class Cached(
		val size: Long?,
		val lastModifiedAtMillis: Long?,
		val text: String?,
	) {
		fun matches(size: Long?, lastModifiedAtMillis: Long?): Boolean =
			this.size == size && this.lastModifiedAtMillis == lastModifiedAtMillis
	}
}
