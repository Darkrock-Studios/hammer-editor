package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface ServerSettingsDatasource {
	fun serverIsSetup(projectsDir: HPath): Boolean
	fun loadServerSettings(projectsDir: HPath): ServerSettings?
	fun storeServerSettings(settings: ServerSettings, projectsDir: HPath)
	fun removeServerSettings(projectsDir: HPath)

	/**
	 * One-shot, idempotent migration of legacy inline `server.json` tokens into the
	 * [AuthTokenStore], rewriting the file tokenless. A no-op when there are no inline
	 * tokens. Performs writes, so it must run after app bootstrap, never during it.
	 *
	 * Transitional: delete once 4.0 ships.
	 */
	fun migrateInlineTokens(projectsDir: HPath)
}