package com.darkrockstudios.apps.hammer.common.data.globalsettings

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokens
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.toPersisted
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.toServerSettings
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import com.darkrockstudios.apps.hammer.common.getPlatformName
import com.darkrockstudios.apps.hammer.common.spellcheck.LanguageUtil
import com.darkrockstudios.apps.hammer.common.spellcheck.findBestMatchingLanguage
import com.darkrockstudios.apps.hammer.common.spellcheck.findBestMatchingLanguageOrNull
import com.darkrockstudios.apps.hammer.common.spellcheck.toLocale
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent
import kotlin.uuid.Uuid

class GlobalSettingsStore(
	private val globalSettingsDatasource: GlobalSettingsDatasource,
	private val serverSettingsDatasource: ServerSettingsDatasource,
	private val authTokenDatasource: AuthTokenDatasource,
) : KoinComponent {

	private val lock = reentrantLock()

	var globalSettings: GlobalSettings
		private set
	private fun projectsDir(): HPath = globalSettings.projectsDirectory.toPath().toHPath()

	private val _globalSettingsUpdates = MutableSharedFlow<GlobalSettings>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val globalSettingsUpdates: SharedFlow<GlobalSettings> = _globalSettingsUpdates

	private val _serverSettingsUpdates = MutableSharedFlow<ServerSettings?>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val serverSettingsUpdates: SharedFlow<ServerSettings?> = _serverSettingsUpdates

	var serverSettings: ServerSettings?
		private set

	init {
		globalSettings = globalSettingsDatasource.loadSettings()
		_globalSettingsUpdates.tryEmit(globalSettings)

		serverSettings = loadServerSettings()
		_serverSettingsUpdates.tryEmit(serverSettings)
	}

	suspend fun updateSettings(action: (GlobalSettings) -> GlobalSettings) {
		val settings = globalSettingsUpdates.first()
		lock.withLock {
			val updated = action(settings)
			globalSettingsDatasource.storeSettings(updated)
			dispatchSettingsUpdate(updated)

			if (settings.projectsDirectory != updated.projectsDirectory) {
				dispatchServerSettingsUpdate(loadServerSettings())
			}
		}
	}

	fun isServerSynchronized(): Boolean = (serverSettings != null)

	private fun dispatchSettingsUpdate(settings: GlobalSettings) {
		globalSettings = settings
		_globalSettingsUpdates.tryEmit(settings)
	}

	private fun loadServerSettings(): ServerSettings? =
		serverSettingsDatasource.loadServerSettings(projectsDir())?.let { account ->
			account.toServerSettings(authTokenDatasource.get(account.url, account.userId))
		}

	fun updateServerSettings(settings: ServerSettings) {
		serverSettingsDatasource.storeServerSettings(settings.toPersisted(), projectsDir())
		authTokenDatasource.put(
			settings.url,
			settings.userId,
			AuthTokens(settings.bearerToken, settings.refreshToken),
		)
		dispatchServerSettingsUpdate(settings)
	}

	fun serverIsSetup(): Boolean = serverSettingsDatasource.serverIsSetup(projectsDir())

	fun defaultProjectDir() = GlobalSettingsStore.defaultProjectDir().toHPath()

	fun deleteServerSettings() {
		serverSettingsDatasource.removeServerSettings(projectsDir())
			?.let { authTokenDatasource.remove(it.url, it.userId) }
		dispatchServerSettingsUpdate(null)
	}

	private fun dispatchServerSettingsUpdate(settings: ServerSettings?) {
		serverSettings = settings
		_serverSettingsUpdates.tryEmit(settings)
	}

	suspend fun userIdOrThrow(): Long {
		return serverSettingsUpdates.first()?.userId
			?: throw IllegalStateException("Server settings missing")
	}

	/**
	 * Returns the stable per-install id, generating and persisting one on first
	 * call. Used both as the server-side install identity (sent during login /
	 * createAccount) and as the local writing-activity device id, so a single
	 * install always presents the same identity in both contexts. Works without
	 * a sync server — generation is purely local.
	 */
	suspend fun ensureInstallId(): String {
		globalSettings.installId?.let { return it }
		updateSettings { current ->
			if (current.installId == null) current.copy(installId = Uuid.random().toString())
			else current
		}
		return checkNotNull(globalSettings.installId) { "installId should be set after ensureInstallId" }
	}

	/** Friendly label for this device. Falls back to the platform name when unset. */
	fun deviceLabelOrDefault(): String = globalSettings.deviceLabel ?: getPlatformName()

	companion object {
		const val DEFAULT_PROJECTS_DIR = "HammerProjects"

		fun defaultProjectDir() = getDefaultRootDocumentDirectory().toPath() / DEFAULT_PROJECTS_DIR

		fun createDefault(
			languageUtil: LanguageUtil,
			platformSpellCheckerFactory: PlatformSpellCheckerFactory
		): GlobalSettings {
			val currentLocale = languageUtil.getCurrentLocale()
			val hasSpForLang = platformSpellCheckerFactory.findBestMatchingLanguageOrNull(currentLocale)
			val language = platformSpellCheckerFactory.findBestMatchingLanguage(currentLocale)

			return GlobalSettings(
				projectsDirectory = defaultProjectDir().toString(),
				spellCheckSettings = SpellCheckerSettings(
					enabled = (hasSpForLang != null),
					locale = language.toLocale()
				),
				// A brand new install has nothing to catch up on, so seed the changelog as seen.
				// Upgrading users have no such key in their TOML and get the popup.
				lastSeenChangelogVersion = BuildMetadata.APP_VERSION,
			)
		}
	}
}