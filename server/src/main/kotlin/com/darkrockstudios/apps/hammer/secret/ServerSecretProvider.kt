package com.darkrockstudios.apps.hammer.secret

import okio.FileSystem
import okio.Path

/**
 * Reads the keyring document from some backend. Read-only at runtime — nothing
 * here ever mints or writes key material. Returns the raw JSON string, or null
 * when no keyring is present (the caller decides whether that is fatal).
 */
interface ServerSecretProvider {
	fun loadKeyring(): String?
}

/** Reads the keyring JSON from a file. */
class FileSecretProvider(
	private val fileSystem: FileSystem,
	private val keyringPath: Path,
) : ServerSecretProvider {
	override fun loadKeyring(): String? =
		if (fileSystem.exists(keyringPath)) {
			fileSystem.read(keyringPath) { readUtf8() }
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
