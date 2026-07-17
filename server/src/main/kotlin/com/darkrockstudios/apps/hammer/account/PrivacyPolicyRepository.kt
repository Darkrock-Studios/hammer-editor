package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.utilities.CachedTextFile
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Serves the optional privacy policy published at `/privacy`. When [ServerConfig.privacyPolicy]
 * points at a readable, non-blank file, [text] returns its contents; otherwise it returns null and
 * the page and footer link are hidden.
 *
 * A configured-but-missing path is rejected at startup (see validateConfigFiles in Application),
 * so [text] only returns null here when no path is configured.
 */
class PrivacyPolicyRepository(
	serverConfig: ServerConfig,
	fileSystem: FileSystem,
) {
	private val file = CachedTextFile(serverConfig.privacyPolicy?.toPath(), fileSystem)

	fun text(): String? = file.read()
}
