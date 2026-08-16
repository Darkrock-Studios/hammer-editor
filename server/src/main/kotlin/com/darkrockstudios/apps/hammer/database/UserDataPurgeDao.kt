package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

/**
 * Hard-deletes every row belonging to one account, for the final stage of
 * account deletion. Deletes run child-tables-first in a single transaction,
 * explicitly rather than relying on ON DELETE CASCADE, because tests disable
 * FK enforcement.
 */
class UserDataPurgeDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val db = database.serverDatabase

	/**
	 * Returns false without deleting anything when the account no longer exists
	 * or is no longer soft-deleted: a restore that lands after the retention job
	 * snapshotted its due batch must win over the purge.
	 */
	suspend fun purgeUserData(userId: Long): Boolean = withContext(ioDispatcher) {
		db.accountQueries.transactionWithResult {
			// Row-locked so a concurrent restore serializes against this transaction.
			val account = db.accountQueries.getAccountForUpdate(userId).executeAsOneOrNull()
			if (account?.deleted_at == null) return@transactionWithResult false

			// Review tree (suggestion -> scene -> request).
			db.reviewSuggestionQueries.deleteAllForUser(userId)
			db.reviewSceneQueries.deleteAllForUser(userId)
			db.reviewRequestQueries.deleteAllForUser(userId)

			// Everything hanging off the user's projects.
			db.publishedStoryReaderQueries.deleteAllForUser(userId)
			db.publishedStoryReaderQueries.deleteAllTotalsForUser(userId)
			db.deletedEntityQueries.deleteAllForUser(userId)
			db.projectAccessSceneQueries.deleteAllForUser(userId)
			db.projectAccessQueries.deleteAllAccessForUser(userId)
			db.storyEntityQueries.deleteAllForUser(userId)
			db.projectDataQueries.deleteAllForUser(userId)
			db.writingActivityQueries.deleteAllForUser(userId)
			db.projectQueries.deleteAllForUser(userId)

			// Account-scoped data.
			db.storyIdeaQueries.deleteAllForUser(userId)
			db.deletedIdeaQueries.deleteAllForUser(userId)
			db.deletedProjectQueries.deleteAllForUser(userId)
			db.userActivityQueries.deleteAllForUser(userId)
			db.authTokenQueries.deleteByUserId(userId)
			db.passwordResetTokenQueries.deleteAllForUser(userId)

			// No-FK tables that would otherwise keep dangling references.
			// login_attempt rows are keyed by lower-cased email rather than user id.
			db.errorLogQueries.deleteAllForUser(userId)
			db.loginAttemptQueries.deleteAllForEmail(account.email.lowercase())

			db.accountQueries.hardDeleteAccount(userId)
			true
		}
	}
}
