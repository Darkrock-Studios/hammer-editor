package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import org.slf4j.Logger
import kotlin.time.Duration.Companion.hours

/**
 * Deletes whitelist entries whose expiry has passed and revokes the sessions of
 * the accounts behind them.
 *
 * Expiry is already enforced in the `isWhiteListed` query, so a lapsed entry stops
 * authorizing the moment it expires regardless of this job. The cadence therefore
 * only bounds how long a still-open session survives, and how long reaped rows
 * linger in the admin list.
 */
class WhitelistExpiryJob(
	private val whiteListRepository: WhiteListRepository,
	private val accountsRepository: AccountsRepository,
	logger: Logger,
) : RecurringTask("Whitelist expiry job", logger) {

	/** One cleanup pass. Public so it can be driven deterministically in tests. */
	override suspend fun tick() {
		// Force logout before deleting, while the lapsing emails are still known.
		val expired = whiteListRepository.getExpiredEntries()
		for (entry in expired) {
			accountsRepository.forceLogout(entry.email)
			logger.info("Whitelist entry expired, access revoked: ${entry.email}")
		}
		whiteListRepository.removeExpired()
	}

	override suspend fun nextDelay() = EXPIRY_CHECK_INTERVAL

	companion object {
		private val EXPIRY_CHECK_INTERVAL = 1.hours
	}
}
