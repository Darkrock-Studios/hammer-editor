package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.AuthToken
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.utilities.toISO8601
import kotlinx.datetime.Instant

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
		val expiresString = expires.toISO8601()
		queries.setToken(
			userId = userId,
			installId = installId,
			token = token.auth,
			refresh = token.refresh,
			expires = expiresString
		)
	}

	suspend fun getTokenByInstallId(userId: Long, installId: String): AuthToken? {
		val query = queries.getTokenByInstallId(userId, installId)
		return query.executeAsOneOrNull()
	}
}