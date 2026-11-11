package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore.Companion.createDefault
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.spellcheck.LanguageUtil
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath

class GlobalSettingsFilesystemDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	private val languageUtil: LanguageUtil,
	private val platformSpellCheckerFactory: PlatformSpellCheckerFactory,
) : GlobalSettingsDatasource {

	override fun loadSettings(): GlobalSettings {
		Logger.i { "Loading Global Settings from: $CONFIG_PATH" }

		if (!fileSystem.exists(CONFIG_PATH)) {
			return createDefault(languageUtil, platformSpellCheckerFactory)
		}

		val settingsText: String = fileSystem.read(CONFIG_PATH) {
			readUtf8()
		}

		// Pure read: a corrupt file is left in place (overwritten on the next store) rather
		// than deleted here, so loading never mutates the filesystem during app bootstrap.
		return try {
			toml.decodeFromString(settingsText)
		} catch (e: NumberFormatException) {
			Logger.e("Failed to load Global Settings, Reverting to defaults.", e)
			createDefault(languageUtil, platformSpellCheckerFactory)
		} catch (e: SerializationException) {
			Logger.e("Failed to load Global Settings, Reverting to defaults.", e)
			createDefault(languageUtil, platformSpellCheckerFactory)
		}
	}

	override fun storeSettings(settings: GlobalSettings) {
		val settingsText = toml.encodeToString(settings)

		fileSystem.createDirectories(CONFIG_PATH.parent!!)
		fileSystem.write(CONFIG_PATH, false) {
			writeUtf8(settingsText)
		}
	}

	companion object {
		private const val FILE_NAME = "global_settings.toml"
		val CONFIG_PATH = getConfigDirectory().toPath() / FILE_NAME
	}
}