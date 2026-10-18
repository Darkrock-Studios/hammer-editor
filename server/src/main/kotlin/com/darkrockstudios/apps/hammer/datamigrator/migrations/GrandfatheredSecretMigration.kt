package com.darkrockstudios.apps.hammer.datamigrator.migrations

import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.encryption.EncryptionModeGuard
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import okio.FileSystem
import okio.Path

class GrandfatheredSecretException(secretPath: Path) : IllegalStateException(
	"This server still relies on the pre-keyring secret at $secretPath, which 4.0 no longer reads. " +
		"Convert it with 'migrate-secret' (never 'generate-keyring', which would make existing data " +
		"unreadable), point [secret] at the resulting keyring, then start the server again."
)

/**
 * Refuses a server whose encryption still depends on a bare `server.secret`, which 4.0 no
 * longer wraps as a keyring. A plaintext server keeps the file as its token-HMAC fallback.
 */
class GrandfatheredSecretMigration(
	private val provider: ServerSecretProvider,
	private val fileSystem: FileSystem,
	private val legacySecretPath: Path,
	private val serverConfig: ServerConfig,
	private val database: Database,
) : DataMigration {
	override val id = "grandfathered_secret_removed"

	override suspend fun migrate() {
		if (provider.loadKeyring() != null) return
		if (!fileSystem.exists(legacySecretPath)) return

		val needsContentKey = serverConfig.encryption.effectiveWriteMode() == EncryptionMode.AES ||
			EncryptionModeGuard.encryptedRowCount(database.serverDatabase) > 0
		if (needsContentKey) throw GrandfatheredSecretException(legacySecretPath)
	}
}
