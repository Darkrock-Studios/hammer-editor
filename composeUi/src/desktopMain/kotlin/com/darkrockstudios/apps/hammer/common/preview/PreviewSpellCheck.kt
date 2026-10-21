package com.darkrockstudios.apps.hammer.common.preview

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokens
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.PersistedServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory

private val previewGlobalSettings = GlobalSettings(
	projectsDirectory = "preview",
)

private val fakeGlobalSettingsDatasource = object : GlobalSettingsDatasource {
	override fun loadSettings(): GlobalSettings = previewGlobalSettings
	override fun storeSettings(settings: GlobalSettings) {}
}

private val fakeServerSettingsDatasource = object : ServerSettingsDatasource {
	override fun serverIsSetup(projectsDir: HPath): Boolean = false
	override fun loadServerSettings(projectsDir: HPath): PersistedServerSettings? = null
	override fun storeServerSettings(settings: PersistedServerSettings, projectsDir: HPath) {}
	override fun removeServerSettings(projectsDir: HPath): PersistedServerSettings? = null
}

private val fakeAuthTokenDatasource = object : AuthTokenDatasource {
	override fun get(url: String, userId: Long): AuthTokens? = null
	override fun put(url: String, userId: Long, tokens: AuthTokens) {}
	override fun remove(url: String, userId: Long) {}
}

/**
 * Real [SpellCheckRepository] backed by in-memory datasources so editor screens
 * (anything using `MarkdownEditField`) can resolve it from the preview Koin graph.
 */
fun fakeSpellCheckRepository(): SpellCheckRepository = SpellCheckRepository(
	globalSettingsStore = GlobalSettingsStore(
		globalSettingsDatasource = fakeGlobalSettingsDatasource,
		serverSettingsDatasource = fakeServerSettingsDatasource,
		authTokenDatasource = fakeAuthTokenDatasource,
	),
	spellCheckFactory = PlatformSpellCheckerFactory(),
)
