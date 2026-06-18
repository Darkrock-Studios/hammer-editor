package com.darkrockstudios.apps.hammer.secret

import com.darkrockstudios.apps.hammer.SecretConfig
import com.darkrockstudios.apps.hammer.SecretProviderType
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Reads the keyring document from some backend. Read-only at runtime — nothing
 * here ever mints or writes key material. Returns the raw JSON string, or null
 * when no keyring is present (the caller decides whether that is fatal).
 */
interface ServerSecretProvider {
	fun loadKeyring(): String?
}

/** Builds the provider for a [SecretConfig]. Shared by DI and the keyring CLI so they can't drift. */
fun buildSecretProvider(config: SecretConfig, fileSystem: FileSystem): ServerSecretProvider =
	when (config.provider) {
		SecretProviderType.FILE -> FileSecretProvider(
			fileSystem,
			config.file?.toPath() ?: KeyringManager.defaultKeyringPath(),
		)
		SecretProviderType.ENV -> EnvSecretProvider(config.envVar)
	}

/** Reads the keyring JSON from a file. An empty/whitespace-only file reads as absent. */
class FileSecretProvider(
	private val fileSystem: FileSystem,
	private val keyringPath: Path,
) : ServerSecretProvider {
	override fun loadKeyring(): String? =
		if (fileSystem.exists(keyringPath)) {
			fileSystem.read(keyringPath) { readUtf8() }.ifBlank { null }
		} else {
			null
		}
}

/** Reads the keyring JSON from an environment variable. */
class EnvSecretProvider(
	private val variableName: String,
	private val readEnv: (String) -> String? = System::getenv,
) : ServerSecretProvider {
	override fun loadKeyring(): String? = readEnv(variableName)?.ifBlank { null }
}
