package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.utilities.CachedTextFile
import okio.FileSystem
import okio.Path.Companion.toPath
import java.security.MessageDigest

/**
 * Serves the optional Terms of Service that new accounts must accept. When
 * [ServerConfig.termsOfService] points at a readable, non-blank file, [challenge] returns the
 * current terms and account creation is gated on accepting its version; otherwise it returns null.
 *
 * A configured-but-missing path is rejected at startup (see validateConfigFiles in Application),
 * so [challenge] only returns null here when no path is configured.
 */
class TermsOfServiceRepository(
	serverConfig: ServerConfig,
	fileSystem: FileSystem,
) {
	private val file = CachedTextFile(serverConfig.termsOfService?.toPath(), fileSystem)

	fun challenge(): TermsOfServiceChallenge? {
		val text = file.read() ?: return null
		return TermsOfServiceChallenge(text = text, version = hash(text))
	}

	private fun hash(text: String): String =
		MessageDigest.getInstance("SHA-256")
			.digest(text.encodeToByteArray())
			.joinToString("") { "%02x".format(it) }
}
