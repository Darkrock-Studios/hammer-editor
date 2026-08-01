package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.database.UserDataPurgeDao
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * User-initiated account deletion. Deletion is two-phase: [softDelete] locks the
 * account out immediately while its data is retained for a configurable window
 * ([com.darkrockstudios.apps.hammer.AccountDeletionConfig.retentionDays]) so an
 * operator can [restore] it; [AccountDeletionJob] calls [hardDelete] for accounts
 * past the window.
 */
class AccountDeletionService(
	private val accountsRepository: AccountsRepository,
	private val penNameService: PenNameService,
	private val whiteListRepository: WhiteListRepository,
	private val userDataPurgeDao: UserDataPurgeDao,
	private val projectsSyncManager: SyncSessionManager<Long, ProjectsSynchronizationSession>,
	private val projectSyncManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>,
	private val clock: Clock,
) {
	/**
	 * Flags the account as deleted and locks it out everywhere: auth tokens are
	 * revoked, live sync sessions terminated, every published and privately shared
	 * story unpublished, and the pen name released for others to claim. The
	 * account row and story data are retained until [hardDelete].
	 *
	 * Admin accounts can never be deleted; an admin must have their admin status
	 * removed first. The markDeleted query enforces the same rule at the data
	 * layer, so no other caller can flag an admin either.
	 */
	suspend fun softDelete(userId: Long): SResult<Unit> {
		val account = accountsRepository.getAccountOrNull(userId)
			?: return SResult.failure("Account not found", Msg.r("api_error_unknown"))

		if (account.deleted_at != null) return SResult.success(Unit)

		if (account.is_admin) {
			return SResult.failure(
				"Admin accounts cannot be deleted",
				Msg.r("account_delete_error_admin")
			)
		}

		// Flag first so every auth gate closes before anything else happens.
		accountsRepository.markDeleted(userId, clock.now())
		accountsRepository.forceLogout(account.email)
		projectsSyncManager.terminateSession(userId)
		projectSyncManager.terminateSessions { it.userId == userId }
		penNameService.releasePenName(userId)
		accountsRepository.updateCommunityMember(userId, false)

		return SResult.success(Unit)
	}

	/**
	 * Un-flags a soft-deleted account so it can log in and sync again. The pen
	 * name and publish state are NOT restored: the pen name was released and may
	 * have been claimed by another account since. Returns false when no such
	 * account exists.
	 */
	suspend fun restore(userId: Long): Boolean {
		accountsRepository.getAccountOrNull(userId) ?: return false
		accountsRepository.restoreDeleted(userId)
		return true
	}

	/** Accounts soft-deleted before [cutoff], due for permanent deletion. */
	suspend fun findAccountsPastRetention(cutoff: Instant): List<Account> {
		return accountsRepository.getSoftDeletedBefore(cutoff)
	}

	/**
	 * Permanently deletes the account and every row it owns, and removes its
	 * email from the whitelist. A no-op when the account is missing or no longer
	 * soft-deleted; the purge transaction re-checks deleted_at under a row lock,
	 * so a restore racing the retention job always wins. Disk caches of rendered
	 * story HTML and OG images are left to DiskCachePruneJob; serving is
	 * DB-gated, so nothing stale stays reachable.
	 */
	suspend fun hardDelete(userId: Long) {
		val account = accountsRepository.getAccountOrNull(userId) ?: return
		if (account.deleted_at == null) return
		if (userDataPurgeDao.purgeUserData(userId)) {
			whiteListRepository.removeFromWhiteList(account.email)
		}
	}
}
