package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import okio.Path.Companion.toPath

/**
 * One-shot migration that relocates legacy inline auth tokens out of `server.json`
 * and into the [com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenStore].
 *
 * This intentionally runs as a startup migration rather than inside
 * [ServerSettingsDatasource.loadServerSettings]: the migration performs guarded
 * filesystem writes, and doing that while [GlobalSettingsStore] is still constructing
 * would re-enter the store's own bootstrap and loop forever. Running here, after the
 * store is built, keeps loads pure. Idempotent — a no-op once tokens are relocated.
 *
 * Transitional: delete once 4.0 ships and every upgrading user has run it.
 */
class MigrateInlineAuthTokens(
	private val globalSettingsStore: GlobalSettingsStore,
	private val serverSettingsDatasource: ServerSettingsDatasource,
) : GlobalMigration {

	override suspend fun migrate() {
		val projectsDir = globalSettingsStore.globalSettings.projectsDirectory.toPath().toHPath()
		serverSettingsDatasource.migrateInlineTokens(projectsDir)
	}
}
