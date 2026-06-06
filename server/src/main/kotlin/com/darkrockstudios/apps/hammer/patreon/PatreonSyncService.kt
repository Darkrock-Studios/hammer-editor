package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import io.ktor.util.*
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import kotlin.time.Clock

@Serializable
data class SyncResult(
	val added: Int,
	val removed: Int,
	val skipped: Int,
	val errors: List<String> = emptyList()
)

class PatreonSyncService(
	private val patreonApiClient: PatreonApiClient,
	private val whiteListRepository: WhiteListRepository,
	private val accountsRepository: AccountsRepository,
	private val authTokenDao: AuthTokenDao,
	private val configRepository: ConfigRepository,
	private val clock: Clock,
	private val logger: Logger
) {
	companion object {
		const val WHITELIST_REASON = "Patreon"
	}

	suspend fun performFullSync(): Result<SyncResult> {
		val config = configRepository.get(AdminServerConfig.PATREON_CONFIG)

		if (!config.enabled) {
			logger.info("Patreon sync skipped - disabled in config")
			return Result.success(SyncResult(added = 0, removed = 0, skipped = 0))
		}

		if (config.campaignId.isBlank() || config.creatorAccessToken.isBlank()) {
			logger.warn("Patreon sync skipped - missing campaign ID or access token")
			return Result.failure(PatreonSyncException("Missing campaign ID or access token"))
		}

		logger.info("Starting Patreon sync for campaign ${config.campaignId}")

		// 1. Get qualifying patrons from Patreon API
		val patreonResult = patreonApiClient.fetchAllQualifyingMembers(
			campaignId = config.campaignId,
			accessToken = config.creatorAccessToken,
			minimumAmountCents = config.minimumAmountCents
		)

		if (patreonResult.isFailure) {
			return Result.failure(patreonResult.exceptionOrNull()!!)
		}

		val patreonEmails = patreonResult.getOrThrow()
			.map { it.trim().toLowerCasePreservingASCIIRules() }
			.toSet()

		logger.info("Found ${patreonEmails.size} qualifying Patreon members")

		// 2. Get current Patreon-managed whitelist entries ONLY
		val currentPatreonEntries = whiteListRepository.getWhiteListByReason(WHITELIST_REASON)
			.map { it.email }
			.toSet()

		logger.info("Found ${currentPatreonEntries.size} existing Patreon whitelist entries")

		// 3. Add new patrons (only if not already whitelisted with ANY reason)
		var addedCount = 0
		var skippedCount = 0
		val errors = mutableListOf<String>()

		for (email in patreonEmails) {
			try {
				if (!whiteListRepository.isOnWhiteList(email)) {
					whiteListRepository.addToWhiteList(email, WHITELIST_REASON)
					logger.info("Added Patreon member to whitelist: $email")
					addedCount++
				} else if (email !in currentPatreonEntries) {
					// Already whitelisted with different reason - skip
					logger.debug("Skipping Patreon member (already whitelisted): $email")
					skippedCount++
				}
				// Per-member: one failure must not abort the whole sync.
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				logger.error("Failed to add Patreon member to whitelist: $email", e)
				errors.add("Failed to add $email: ${e.message}")
			}
		}

		// 4. Remove lapsed patrons + force logout (only Patreon-managed entries)
		var removedCount = 0
		val toRemove = currentPatreonEntries - patreonEmails

		for (email in toRemove) {
			try {
				whiteListRepository.removeFromWhiteList(email)
				forceLogout(email)
				logger.info("Removed lapsed Patreon member from whitelist: $email")
				removedCount++
			} catch (e: Exception) {
				logger.error("Failed to remove Patreon member from whitelist: $email", e)
				errors.add("Failed to remove $email: ${e.message}")
			}
		}

		// Update last sync time
		val updatedConfig = config.copy(lastSync = clock.now().toString())
		configRepository.set(AdminServerConfig.PATREON_CONFIG, updatedConfig)

		val result = SyncResult(
			added = addedCount,
			removed = removedCount,
			skipped = skippedCount,
			errors = errors
		)

		logger.info("Patreon sync completed: added=$addedCount, removed=$removedCount, skipped=$skippedCount, errors=${errors.size}")

		return Result.success(result)
	}

	suspend fun forceLogout(email: String) {
		val account = accountsRepository.findAccount(email)
		if (account != null) {
			authTokenDao.deleteTokensByUserId(account.id)
			logger.info("Force logged out user: $email")
		}
	}

	suspend fun getPatreonWhitelistCount(): Long {
		return whiteListRepository.getWhiteListByReason(WHITELIST_REASON).size.toLong()
	}

	suspend fun getPatreonMembersWithAccountsCount(): Long {
		return whiteListRepository.countByReasonWithAccounts(WHITELIST_REASON)
	}
}

class PatreonSyncException(message: String) : Exception(message)
