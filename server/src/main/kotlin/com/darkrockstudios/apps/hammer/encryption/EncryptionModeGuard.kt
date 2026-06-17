package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.database.ServerDatabase

class EncryptionModeMismatchException(encryptedRowCount: Long) : IllegalStateException(
	"Encryption mode is 'none' but $encryptedRowCount encrypted row(s) exist. This server " +
		"holds AES-encrypted data; running in plaintext mode would leave it unreadable on write " +
		"and is almost certainly an unintended downgrade. Set [encryption] mode = \"aes\" in your " +
		"server config to keep encryption enabled."
)

/**
 * Refuses to boot a server in plaintext mode when AES-encrypted rows already
 * exist — the previously-encrypted-deployment guard. A row counts as encrypted
 * when its cipher tag is anything other than plaintext.
 */
object EncryptionModeGuard {
	fun verifyOnBoot(mode: EncryptionMode, db: ServerDatabase) {
		if (mode != EncryptionMode.NONE) return

		val encryptedRows = db.storyEntityQueries.countEncrypted().executeAsOne() +
			db.reviewSceneQueries.countEncrypted().executeAsOne()

		if (encryptedRows > 0) throw EncryptionModeMismatchException(encryptedRows)
	}
}
