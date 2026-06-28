package com.darkrockstudios.apps.hammer.common.preview

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
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
	override fun loadServerSettings(projectsDir: HPath): ServerSettings? = null
	override fun storeServerSettings(settings: ServerSettings, projectsDir: HPath) {}
	override fun removeServerSettings(projectsDir: HPath) {}
	override fun migrateInlineTokens(projectsDir: HPath) {}
}

/**
 * Real [SpellCheckRepository] backed by in-memory datasources so editor screens
 * (anything using `MarkdownEditField`) can resolve it from the preview Koin graph.
 */
fun fakeSpellCheckRepository(): SpellCheckRepository = SpellCheckRepository(
	globalSettingsStore = GlobalSettingsStore(
		globalSettingsDatasource = fakeGlobalSettingsDatasource,
		serverSettingsDatasource = fakeServerSettingsDatasource,
	),
	spellCheckFactory = PlatformSpellCheckerFactory(),
)
