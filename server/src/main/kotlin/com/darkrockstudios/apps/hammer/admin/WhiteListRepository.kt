package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import io.ktor.util.*
import org.koin.core.component.KoinComponent

class WhiteListRepository(
	private val whiteListDao: WhiteListDao,
	private val configRepository: ConfigRepository,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	suspend fun useWhiteList(): Boolean {
		return configRepository.get(AdminServerConfig.WHITELIST_ENABLED)
	}

	suspend fun setWhiteListEnabled(enabled: Boolean) {
		configRepository.set(AdminServerConfig.WHITELIST_ENABLED, enabled)
	}

	suspend fun getWhiteList(): List<String> {
		return whiteListDao.getAllWhiteListedEmails()
	}

	suspend fun getWhiteList(page: Int, pageSize: Int): List<String> {
		val limit = pageSize.toLong()
		val offset = (page * pageSize).toLong()
		return whiteListDao.getWhiteListPaginated(limit, offset)
	}

	suspend fun getWhiteListCount(): Long {
		return whiteListDao.getWhiteListCount()
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

	private fun cleanEmail(email: String): String {
		val cleanedEmail = email.trim().toLowerCasePreservingASCIIRules()
		return cleanedEmail
	}
}