package com.darkrockstudios.apps.hammer.datamigrator.migrations

import com.darkrockstudios.apps.hammer.admin.WhiteListRepository

/**
 * Seeds the allowed users list with every existing, non-deleted account so servers
 * upgrading from the optional-whitelist era don't lock out their users.
 */
class AllowedUsersBackfillMigration(
	private val whiteListRepository: WhiteListRepository,
) : DataMigration {
	override val id = "allowedusers_backfill"

	override suspend fun migrate() {
		whiteListRepository.backfillFromAccounts()
	}
}
