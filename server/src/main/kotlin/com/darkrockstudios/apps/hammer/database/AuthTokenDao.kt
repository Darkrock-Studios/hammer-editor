package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.http.Token
import kotlin.time.Instant

class AuthTokenDao(database: Database) {
	private val queries = database.serverDatabase.authTokenQueries

	suspend fun getTokenByAuthToken(token: String): AuthToken? {
		val query = queries.getTokenByToken(token)
		return query.executeAsOneOrNull()
	}

	suspend fun setToken(
		userId: Long,
		installId: String,
		token: Token,
		expires: Instant
	) {
		queries.setToken(
			userId = userId,
			installId = installId,
			token = token.auth,
			refresh = token.refresh,
			expires = expires
		)
	}

	suspend fun getTokenByInstallId(userId: Long, installId: String): AuthToken? {
		val query = queries.getTokenByInstallId(userId, installId)
		return query.executeAsOneOrNull()
	}

	suspend fun deleteTokensByUserId(userId: Long) {
		queries.deleteByUserId(userId)
	}

	suspend fun deleteExpiredBefore(cutoff: Instant) {
		queries.deleteExpiredBefore(cutoff)
	}
}
