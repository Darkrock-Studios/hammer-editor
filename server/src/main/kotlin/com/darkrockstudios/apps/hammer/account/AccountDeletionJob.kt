package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import kotlinx.coroutines.CancellationException
import org.slf4j.Logger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Hard-deletes accounts whose soft-delete retention window has elapsed.
 * Enforcement lives in the auth gates: a soft-deleted account is already locked
 * out, so this job only performs the final purge and its cadence never affects
 * correctness.
 */
class AccountDeletionJob(
	private val accountDeletionService: AccountDeletionService,
	private val serverConfig: ServerConfig,
	private val clock: Clock,
	logger: Logger,
) : RecurringTask("Account deletion job", logger) {

	override suspend fun tick() {
		val cutoff = clock.now() - serverConfig.accountDeletion.retention
		val dueAccounts = accountDeletionService.findAccountsPastRetention(cutoff)
		for (account in dueAccounts) {
			try {
				accountDeletionService.hardDelete(account.id)
				logger.info("Account past retention hard-deleted: ${account.id}")
			} catch (e: CancellationException) {
				throw e
				// One account failing must not starve the rest of the batch.
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				logger.error("Hard delete failed for account ${account.id}", e)
			}
		}
	}

	override suspend fun nextDelay() = CHECK_INTERVAL

	companion object {
		private val CHECK_INTERVAL = 24.hours
	}
}
