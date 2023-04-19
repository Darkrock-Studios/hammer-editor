package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.getRootDataDirectory
import com.darkrockstudios.apps.hammer.readJson
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import com.darkrockstudios.apps.hammer.writeJson
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import org.koin.core.component.KoinComponent

class WhiteListRepository(
	private val whiteListDao: WhiteListDao,
	private val fileSystem: FileSystem,
	private val json: Json,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	suspend fun initialize() {
		val file = getConfigFile()
		if (fileSystem.exists(file).not()) {
			storeConfig(WhiteListConfig())
		}
	}

	suspend fun useWhiteList(): Boolean {
		val config = loadConfig()
		return config.enabled
	}

	suspend fun setWhiteListEnabled(enabled: Boolean) {
		updateConfig {
			it.copy(enabled = enabled)
		}
	}

	suspend fun getWhiteList(): List<String> {
		return whiteListDao.getAllWhiteListedEmails()
	}

	suspend fun isOnWhiteList(email: String): Boolean {
		return whiteListDao.isWhiteListed(email)
	}

	suspend fun addToWhiteList(email: String) {
		whiteListDao.addToWhiteList(email)
	}

	suspend fun removeFromWhiteList(email: String) {
		whiteListDao.removeFromWhiteList(email)
	}

	private fun getConfigFile(): Path {
		return getRootDataDirectory(fileSystem) / FILENAME
	}

	private suspend fun loadConfig(): WhiteListConfig = withContext(ioDispatcher) {
		val file = getConfigFile()
		val config = fileSystem.readJson<WhiteListConfig>(file, json)
		return@withContext if (config == null) {
			val default = WhiteListConfig()
			fileSystem.writeJson(file, json, default)
			default
		} else {
			config
		}
	}

	private suspend fun storeConfig(config: WhiteListConfig) = withContext(ioDispatcher) {
		val file = getConfigFile()
		fileSystem.writeJson(file, json, config)
	}

	private suspend fun updateConfig(block: (WhiteListConfig) -> WhiteListConfig) {
		val original = loadConfig()
		val updated = block(original)
		storeConfig(updated)
	}

	companion object {
		private const val FILENAME = "whitelist_config.json"
	}
}