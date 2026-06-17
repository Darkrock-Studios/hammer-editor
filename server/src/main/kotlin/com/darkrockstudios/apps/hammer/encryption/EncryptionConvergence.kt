package com.darkrockstudios.apps.hammer.encryption

import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatabaseDatasource
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class EncryptionConvergenceException(message: String) : IllegalStateException(message)

data class ConvergenceReport(val storyEntities: Int, val reviewScenes: Int) {
	val total: Int get() = storyEntities + reviewScenes
}

data class ConvergenceDryRun(
	val storyEntities: Long,
	val reviewScenes: Long,
	/** Entities that would exceed the size cap once encrypted; convergence would fail on these. */
	val overCapEntities: List<String>,
) {
	val total: Long get() = storyEntities + reviewScenes
}

/**
 * Re-crypts every content row onto a target cipher (the active encryptor's tag).
 * Works on the raw stored blobs — no JSON deserialization — re-encrypting with
 * the target key and rewriting the cipher tag, never the hash (cipher ⊥ hash).
 *
 * Resumable by construction: each row's tag is its own progress marker, so a
 * crashed run is finished by simply re-running (the predicate re-selects only
 * rows not yet on target). Each row update is atomic.
 */
class EncryptionConvergence(
	database: Database,
	private val accountDao: AccountDao,
	private val registry: ContentEncryptorRegistry,
	private val maxContentLength: Int = ProjectEntityDatabaseDatasource.MAX_ENTITY_CONTENT_LENGTH,
	private val batchSize: Long = 500,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val story = database.serverDatabase.storyEntityQueries
	private val review = database.serverDatabase.reviewSceneQueries

	/** Rows not yet on [targetTag] — the completion signal; 0 means fully converged. */
	suspend fun remaining(targetTag: String): Long = withContext(ioDispatcher) {
		story.countForConvergence(targetTag).executeAsOne() +
			review.countForConvergence(targetTag).executeAsOne()
	}

	/**
	 * Reports what a convergence to [target] would do without writing anything:
	 * how many rows are off-target, and which entities would exceed the size cap
	 * once encrypted (the rows that would make a real run fail).
	 */
	suspend fun dryRun(target: ContentEncryptor): ConvergenceDryRun = withContext(ioDispatcher) {
		val targetTag = target.cipherName()
		val secrets = HashMap<Long, String>()

		suspend fun secretFor(userId: Long): String {
			secrets[userId]?.let { return it }
			val secret = accountDao.getAccount(userId)?.cipher_secret
				?: error("User $userId not found during dry run")
			secrets[userId] = secret
			return secret
		}

		val storyRemaining = story.countForConvergence(targetTag).executeAsOne()
		val reviewRemaining = review.countForConvergence(targetTag).executeAsOne()

		val overCap = mutableListOf<String>()
		val rows = story.selectForConvergence(targetTag, Long.MAX_VALUE).executeAsList()
		for (row in rows) {
			val secret = secretFor(row.user_id)
			val plain = registry.resolve(row.cipher).decrypt(row.content, secret)
			if (target.encrypt(plain, secret).length > maxContentLength) {
				overCap += "entity ${row.id} (user ${row.user_id}, project ${row.project_id})"
			}
		}

		ConvergenceDryRun(storyRemaining, reviewRemaining, overCap)
	}

	suspend fun converge(target: ContentEncryptor): ConvergenceReport = withContext(ioDispatcher) {
		val targetTag = target.cipherName()
		val secrets = HashMap<Long, String>()

		suspend fun secretFor(userId: Long): String {
			secrets[userId]?.let { return it }
			val secret = accountDao.getAccount(userId)?.cipher_secret
				?: error("User $userId not found during convergence")
			secrets[userId] = secret
			return secret
		}

		var storyCount = 0
		while (true) {
			val batch = story.selectForConvergence(targetTag, batchSize).executeAsList()
			if (batch.isEmpty()) break
			for (row in batch) {
				val secret = secretFor(row.user_id)
				val plain = registry.resolve(row.cipher).decrypt(row.content, secret)
				val reEncrypted = target.encrypt(plain, secret)
				if (reEncrypted.length > maxContentLength) {
					throw EncryptionConvergenceException(
						"Entity ${row.id} (user ${row.user_id}, project ${row.project_id}) would be " +
							"${reEncrypted.length} bytes once encrypted, over the $maxContentLength cap. " +
							"Shrink or split it, then re-run convergence."
					)
				}
				story.updateContentCipher(reEncrypted, targetTag, row.user_id, row.project_id, row.id)
				storyCount++
			}
		}

		var reviewCount = 0
		while (true) {
			val batch = review.selectForConvergence(targetTag, batchSize).executeAsList()
			if (batch.isEmpty()) break
			for (row in batch) {
				val secret = secretFor(row.user_id)
				val plain = registry.resolve(row.cipher).decrypt(row.snapshot_content, secret)
				val reEncrypted = target.encrypt(plain, secret)
				review.updateContentCipher(reEncrypted, targetTag, row.id)
				reviewCount++
			}
		}

		ConvergenceReport(storyCount, reviewCount)
	}
}
