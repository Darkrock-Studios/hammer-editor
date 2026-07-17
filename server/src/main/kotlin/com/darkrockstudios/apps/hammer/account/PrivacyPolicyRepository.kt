package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.ServerConfig
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Serves the optional privacy policy published at `/privacy`. When [ServerConfig.privacyPolicy]
 * points at a readable, non-blank file, [text] returns its contents; otherwise it returns null and
 * the page and footer link are hidden.
 *
 * A configured-but-missing path is rejected at startup (see validatePrivacyPolicy in Application),
 * so [text] only returns null here when no path is configured.
 */
class PrivacyPolicyRepository(
	serverConfig: ServerConfig,
	private val fileSystem: FileSystem,
) {
	private val path = serverConfig.privacyPolicy?.toPath()

	private var cached: Cached? = null

	@Synchronized
	fun text(): String? {
		val path = path ?: return null

		val metadata = fileSystem.metadataOrNull(path) ?: return null
		if (!metadata.isRegularFile) return null

		val cached = cached
		if (cached != null && cached.matches(metadata.size, metadata.lastModifiedAtMillis)) {
			return cached.text
		}

		val text = fileSystem.read(path) { readUtf8() }
		if (text.isBlank()) {
			this.cached = null
			return null
		}

		this.cached = Cached(metadata.size, metadata.lastModifiedAtMillis, text)
		return text
	}

	private class Cached(
		val size: Long?,
		val lastModifiedAtMillis: Long?,
		val text: String,
	) {
		fun matches(size: Long?, lastModifiedAtMillis: Long?): Boolean =
			this.size == size && this.lastModifiedAtMillis == lastModifiedAtMillis
	}
}
