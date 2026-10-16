package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.account.mfa.MfaRepository
import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import org.slf4j.Logger
import kotlin.time.Duration.Companion.hours

/**
 * Periodically deletes auth credentials that can no longer be used: auth_token
 * rows whose refresh window has fully elapsed, consumed/expired password
 * reset tokens, and expired MFA login challenges. Pure housekeeping — an expired
 * row is already rejected at auth time, so this only bounds table growth.
 */
class TokenMaintenanceJob(
	private val accountsRepository: AccountsRepository,
	private val passwordResetRepository: PasswordResetRepository,
	private val mfaRepository: MfaRepository,
	logger: Logger,
) : RecurringTask("Token maintenance job", logger) {

	/** One cleanup pass. Public so it can be driven deterministically in tests. */
	override suspend fun tick() {
		accountsRepository.purgeExpiredTokens()
		passwordResetRepository.cleanupExpiredTokens()
		mfaRepository.purgeExpiredChallenges()
	}

	override suspend fun nextDelay() = PURGE_INTERVAL

	companion object {
		private val PURGE_INTERVAL = 24.hours
	}
}
