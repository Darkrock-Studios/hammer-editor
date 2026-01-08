package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import com.darkrockstudios.apps.hammer.utilities.toSqliteDateTimeString
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.time.Instant

class PasswordResetTokenDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.passwordResetTokenQueries

	suspend fun getTokenByToken(token: String): PasswordResetToken? = withContext(ioDispatcher) {
		queries.getTokenByToken(token).executeAsOneOrNull()
	}

	suspend fun createToken(userId: Long, token: String, expires: Instant) = withContext(ioDispatcher) {
		val expiresString = expires.toSqliteDateTimeString()
		queries.createToken(userId, token, expiresString)
	}

	suspend fun markTokenAsUsed(token: String) = withContext(ioDispatcher) {
		queries.markTokenAsUsed(token)
	}

	suspend fun deleteExpiredTokens() = withContext(ioDispatcher) {
		queries.deleteExpiredTokens()
	}

	suspend fun getRecentTokenCount(userId: Long, since: Instant): Long = withContext(ioDispatcher) {
		val sinceString = since.toSqliteDateTimeString()
		queries.getRecentTokenCount(userId, sinceString).executeAsOne()
	}
}
