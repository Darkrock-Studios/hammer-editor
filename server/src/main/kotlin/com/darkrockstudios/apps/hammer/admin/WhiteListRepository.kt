package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.database.WhiteList
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import io.ktor.util.*
import org.koin.core.component.KoinComponent
import kotlin.time.Clock

class WhiteListRepository(
	private val whiteListDao: WhiteListDao,
	private val configRepository: ConfigRepository,
	private val clock: Clock,
) : KoinComponent {

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

	suspend fun getWhiteListWithDetails(page: Int, pageSize: Int): List<WhiteList> {
		val limit = pageSize.toLong()
		val offset = (page * pageSize).toLong()
		return whiteListDao.getPaginated(limit, offset)
	}

	suspend fun getWhiteListCount(): Long {
		return whiteListDao.getWhiteListCount()
	}

	suspend fun isOnWhiteList(email: String): Boolean {
		val cleanedEmail = cleanEmail(email)
		return whiteListDao.isWhiteListed(cleanedEmail)
	}

	suspend fun addToWhiteList(email: String, reason: String = "Added by admin") {
		val cleanedEmail = cleanEmail(email)
		val dateAdded = clock.now().epochSeconds
		whiteListDao.addToWhiteList(cleanedEmail, dateAdded, reason)
	}

	suspend fun removeFromWhiteList(email: String) {
		val cleanedEmail = cleanEmail(email)
		whiteListDao.removeFromWhiteList(cleanedEmail)
	}

	private fun cleanEmail(email: String): String {
		val cleanedEmail = email.trim().toLowerCasePreservingASCIIRules()
		return cleanedEmail
	}

	fun validateEmail(email: String): Boolean {
		return AccountsRepository.validateEmail(email)
	}

	fun validateReason(reason: String): Boolean {
		val trimmed = reason.trim()
		return trimmed.length <= MAX_REASON_LENGTH
	}

	companion object {
		const val MAX_REASON_LENGTH = 32
	}
}