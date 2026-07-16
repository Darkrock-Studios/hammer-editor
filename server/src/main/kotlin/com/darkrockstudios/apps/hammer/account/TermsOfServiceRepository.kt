package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import okio.FileSystem
import okio.Path.Companion.toPath
import java.security.MessageDigest

/**
 * Serves the optional Terms of Service that new accounts must accept. When
 * [ServerConfig.termsOfService] points at a readable, non-blank file, [challenge] returns the
 * current terms and account creation is gated on accepting its version; otherwise it returns null.
 */
class TermsOfServiceRepository(
	serverConfig: ServerConfig,
	private val fileSystem: FileSystem,
) {
	private val path = serverConfig.termsOfService?.toPath()

	private var cached: Cached? = null

	@Synchronized
	fun challenge(): TermsOfServiceChallenge? {
		val path = path ?: return null

		val metadata = fileSystem.metadataOrNull(path) ?: return null
		if (!metadata.isRegularFile) return null

		val cached = cached
		if (cached != null && cached.matches(metadata.size, metadata.lastModifiedAtMillis)) {
			return cached.challenge
		}

		val text = fileSystem.read(path) { readUtf8() }
		if (text.isBlank()) {
			this.cached = null
			return null
		}

		val challenge = TermsOfServiceChallenge(text = text, version = hash(text))
		this.cached = Cached(metadata.size, metadata.lastModifiedAtMillis, challenge)
		return challenge
	}

	private fun hash(text: String): String =
		MessageDigest.getInstance("SHA-256")
			.digest(text.encodeToByteArray())
			.joinToString("") { "%02x".format(it) }

	private class Cached(
		val size: Long?,
		val lastModifiedAtMillis: Long?,
		val challenge: TermsOfServiceChallenge,
	) {
		fun matches(size: Long?, lastModifiedAtMillis: Long?): Boolean =
			this.size == size && this.lastModifiedAtMillis == lastModifiedAtMillis
	}
}
