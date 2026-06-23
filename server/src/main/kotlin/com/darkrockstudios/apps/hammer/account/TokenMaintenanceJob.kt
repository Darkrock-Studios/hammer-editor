package com.darkrockstudios.apps.hammer.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger
import kotlin.time.Duration.Companion.hours

/**
 * Periodically deletes auth credentials that can no longer be used: auth_token
 * rows whose refresh window has fully elapsed, and consumed/expired password
 * reset tokens. Pure housekeeping — an expired row is already rejected at auth
 * time, so this only bounds table growth.
 */
class TokenMaintenanceJob(
	private val accountsRepository: AccountsRepository,
	private val passwordResetRepository: PasswordResetRepository,
	private val logger: Logger,
) {
	private var job: Job? = null

	fun start(scope: CoroutineScope) {
		if (job?.isActive == true) {
			logger.info("Token maintenance job already running")
			return
		}
		job = scope.launch {
			logger.info("Starting token maintenance job")
			loop()
		}
	}

	fun stop() {
		job?.cancel()
		job = null
		logger.info("Token maintenance job stopped")
	}

	private suspend fun loop() {
		while (currentCoroutineContext().isActive) {
			try {
				tick()
			} catch (e: CancellationException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				logger.error("Error in token maintenance loop", e)
			}
			delay(PURGE_INTERVAL)
		}
	}

	/** One cleanup pass. Public so it can be driven deterministically in tests. */
	suspend fun tick() {
		accountsRepository.purgeExpiredTokens()
		passwordResetRepository.cleanupExpiredTokens()
	}

	companion object {
		private val PURGE_INTERVAL = 24.hours
	}
}
