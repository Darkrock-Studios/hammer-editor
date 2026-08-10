package com.darkrockstudios.apps.hammer.datamigrator.migrations

import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.ServerConfigKey
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository

/**
 * Seeds the allowed users list with every existing, non-deleted account so servers
 * upgrading from the optional-whitelist era don't lock out their users.
 *
 * Only runs when the server had explicitly disabled the old whitelist. On an
 * enforcing server everyone active was already on the list, and a missing entry
 * there means access was deliberately revoked (expired invite, lapsed patron,
 * manual removal); backfilling would silently undo those revocations.
 */
class AllowedUsersBackfillMigration(
	private val whiteListRepository: WhiteListRepository,
	private val configRepository: ConfigRepository,
) : DataMigration {
	override val id = "allowedusers_backfill"

	override suspend fun migrate() {
		if (configRepository.get(LEGACY_WHITELIST_ENABLED).not()) {
			whiteListRepository.backfillFromAccounts()
		}
	}

	companion object {
		/** The removed toggle's row survives in server_config; it defaulted to enabled. */
		private val LEGACY_WHITELIST_ENABLED = ServerConfigKey.boolean("whitelist_enabled", true)
	}
}
