package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.base.validate.EmailValidator
import com.darkrockstudios.apps.hammer.database.WhiteList
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import io.ktor.util.*
import org.koin.core.component.KoinComponent
import kotlin.time.Clock
import kotlin.time.Instant

class WhiteListRepository(
	private val whiteListDao: WhiteListDao,
	private val clock: Clock,
) : KoinComponent {

	suspend fun getWhiteList(): List<String> {
		return whiteListDao.getAllWhiteListedEmails()
	}

	suspend fun getWhiteList(page: Int, pageSize: Int): List<String> {
		val limit = pageSize.toLong()
		val offset = (page * pageSize).toLong()
		return whiteListDao.getWhiteListPaginated(limit, offset)
	}

	suspend fun getWhiteListWithDetails(page: Int, pageSize: Int, sortOldestFirst: Boolean = false): List<WhiteList> {
		val limit = pageSize.toLong()
		val offset = (page * pageSize).toLong()
		return whiteListDao.getPaginated(limit, offset, sortOldestFirst)
	}

	/** A blank [emailSearch] lists everything; otherwise only emails containing it. */
	suspend fun getWhiteListWithAccountStatus(
		page: Int,
		pageSize: Int,
		sortOldestFirst: Boolean = false,
		emailSearch: String? = null,
	) = if (emailSearch.isNullOrBlank()) {
		whiteListDao.getPaginatedWithAccountStatus(
			limit = pageSize.toLong(),
			offset = (page * pageSize).toLong(),
			sortOldestFirst = sortOldestFirst
		)
	} else {
		whiteListDao.searchPaginatedWithAccountStatus(
			pattern = emailSearchPattern(emailSearch),
			limit = pageSize.toLong(),
			offset = (page * pageSize).toLong(),
			sortOldestFirst = sortOldestFirst
		)
	}

	suspend fun getWhiteListCount(emailSearch: String? = null): Long {
		return if (emailSearch.isNullOrBlank()) {
			whiteListDao.getWhiteListCount()
		} else {
			whiteListDao.searchCount(emailSearchPattern(emailSearch))
		}
	}

	/** False for an entry whose expiry has passed, even if the reaping job hasn't run yet. */
	suspend fun isOnWhiteList(email: String): Boolean {
		val cleanedEmail = cleanEmail(email)
		return whiteListDao.isWhiteListed(cleanedEmail, clock.now())
	}

	/** A null [expires] never expires. */
	suspend fun addToWhiteList(email: String, reason: String = "Added by admin", expires: Instant? = null) {
		val cleanedEmail = cleanEmail(email)
		whiteListDao.addToWhiteList(cleanedEmail, clock.now(), reason, expires)
	}

	/** Adds every non-deleted account to the list; existing entries keep their reason and expiry. */
	suspend fun backfillFromAccounts(reason: String = REASON_EXISTING_ACCOUNT) {
		whiteListDao.backfillFromAccounts(clock.now(), reason)
	}

	suspend fun getEntry(email: String): WhiteList? {
		val cleanedEmail = cleanEmail(email)
		return whiteListDao.getByEmail(cleanedEmail)
	}

	suspend fun updateExpiry(email: String, expires: Instant?) {
		val cleanedEmail = cleanEmail(email)
		whiteListDao.updateExpiry(cleanedEmail, expires)
	}

	suspend fun getExpiredEntries(): List<WhiteList> {
		return whiteListDao.getExpired(clock.now())
	}

	suspend fun removeExpired() {
		whiteListDao.deleteExpired(clock.now())
	}

	suspend fun removeFromWhiteList(email: String) {
		val cleanedEmail = cleanEmail(email)
		whiteListDao.removeFromWhiteList(cleanedEmail)
	}

	suspend fun updateReason(email: String, reason: String) {
		val cleanedEmail = cleanEmail(email)
		whiteListDao.updateReason(cleanedEmail, reason)
	}

	suspend fun getWhiteListByReason(reason: String): List<WhiteList> {
		return whiteListDao.getByReason(reason)
	}

	suspend fun countByReasonWithAccounts(reason: String): Long {
		return whiteListDao.countByReasonWithAccounts(reason)
	}

	private fun cleanEmail(email: String): String {
		val cleanedEmail = email.trim().toLowerCasePreservingASCIIRules()
		return cleanedEmail
	}

	fun validateEmail(email: String): Boolean {
		return EmailValidator.validate(email)
	}

	fun validateReason(reason: String): Boolean {
		val trimmed = reason.trim()
		return trimmed.length <= MAX_REASON_LENGTH && trimmed.isNotBlank()
	}

	/** A null [expires] means never expires and is always valid. */
	fun validateExpiry(expires: Instant?): Boolean {
		return expires == null || expires > clock.now()
	}

	companion object {
		const val MAX_REASON_LENGTH = 32
		const val REASON_EXISTING_ACCOUNT = "Existing account"

		/** `%` and `_` are LIKE wildcards; an admin typing `a_b@` means those literally. */
		internal fun emailSearchPattern(raw: String): String {
			val escaped = raw.trim()
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_")
			return "%$escaped%"
		}
	}
}