package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.base.http.readJson
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import io.ktor.util.toLowerCasePreservingASCIIRules
import kotlinx.coroutines.withContext
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
		val cleanedEmail = cleanEmail(email)
		return whiteListDao.isWhiteListed(cleanedEmail)
	}

	suspend fun addToWhiteList(email: String) {
		val cleanedEmail = cleanEmail(email)
		whiteListDao.addToWhiteList(cleanedEmail)
	}

	suspend fun removeFromWhiteList(email: String) {
		val cleanedEmail = cleanEmail(email)
		whiteListDao.removeFromWhiteList(cleanedEmail)
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
		fileSystem.createDirectories(file.parent ?: error("Failed to get store directory"))
		fileSystem.writeJson(file, json, config)
	}

	private suspend fun updateConfig(block: (WhiteListConfig) -> WhiteListConfig) {
		val original = loadConfig()
		val updated = block(original)
		storeConfig(updated)
	}

	private fun cleanEmail(email: String): String {
		val cleanedEmail = email.trim().toLowerCasePreservingASCIIRules()
		return cleanedEmail
	}

	companion object {
		private const val FILENAME = "whitelist_config.json"
	}
}