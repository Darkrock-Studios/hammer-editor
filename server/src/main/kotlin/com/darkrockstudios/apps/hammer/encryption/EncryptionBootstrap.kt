package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import org.slf4j.LoggerFactory

class UnspecifiedEncryptionModeException(encryptedRowCount: Long) : IllegalStateException(
	"Encryption mode is unspecified but $encryptedRowCount encrypted row(s) exist. This server " +
		"already holds encrypted data; refusing to guess what to do. Set [encryption] mode = \"aes\" " +
		"to keep encryption, or mode = \"none\" to converge everything to plaintext."
)

/**
 * Pre-launch encryption gate. Resolves the target cipher from the configured
 * mode, then converges every content row onto it before the server serves
 * traffic. A normal boot (target == last-applied) skips the scan entirely.
 *
 * An unspecified mode on a server that already holds encrypted data is a hard
 * stop — the admin must choose `aes` or `none` rather than silently downgrade.
 */
class EncryptionBootstrap(
	private val activeEncryptor: ContentEncryptor,
	private val convergence: EncryptionConvergence,
	private val configDao: ServerConfigDao,
	private val database: Database,
) {
	private val log = LoggerFactory.getLogger(EncryptionBootstrap::class.java)

	suspend fun run(mode: EncryptionMode?) {
		if (mode == null) {
			val encrypted = EncryptionModeGuard.encryptedRowCount(database.serverDatabase)
			if (encrypted > 0) throw UnspecifiedEncryptionModeException(encrypted)
		}

		// Resolving the active encryptor's tag requires the keyring under mode=aes,
		// so a missing keyring fails fast here.
		val targetTag = activeEncryptor.cipherName()

		val lastApplied = configDao.getConfig(LAST_APPLIED_KEY)
		if (lastApplied == targetTag) {
			log.info("Encryption already converged to '$targetTag'; skipping scan.")
			return
		}

		log.info("Converging content encryption to '$targetTag' (this may take a while on a large database)...")
		val report = convergence.converge(activeEncryptor)
		configDao.upsertConfig(LAST_APPLIED_KEY, targetTag)
		log.info(
			"Converged ${report.total} row(s) to '$targetTag' " +
				"(${report.storyEntities} entities, ${report.reviewScenes} review scenes)."
		)
	}

	/** Reports what convergence to the configured target would do, writing nothing. */
	suspend fun dryRun(): ConvergenceDryRun = convergence.dryRun(activeEncryptor)

	companion object {
		const val LAST_APPLIED_KEY = "encryption.lastAppliedTarget"
	}
}
