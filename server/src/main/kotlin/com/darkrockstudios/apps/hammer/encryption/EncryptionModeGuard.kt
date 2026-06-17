package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.database.ServerDatabase

/** Counts rows whose content is encrypted (any non-plaintext cipher tag). */
object EncryptionModeGuard {
	fun encryptedRowCount(db: ServerDatabase): Long =
		db.storyEntityQueries.countEncrypted().executeAsOne() +
			db.reviewSceneQueries.countEncrypted().executeAsOne()
}
