package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.AuthToken
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.utilities.toISO8601
import kotlinx.datetime.Instant

class AuthTokenDao(database: Database) {
    private val queries = database.serverDatabase.authTokenQueries

    suspend fun getTokensByEmail(email: String): List<AuthToken> {
        val query = queries.getTokensByEmail(email)
        return query.executeAsList()
    }

    suspend fun getTokenByAuthToken(token: String): AuthToken? {
        val query = queries.getTokenByToken(token)
        return query.executeAsOneOrNull()
    }

    suspend fun setToken(
        email: String,
        deviceId: String,
        token: Token,
        expires: Instant
    ) {
        val expiresString = expires.toISO8601()
        queries.setToken(
            email = email,
            deviceId = deviceId,
            token = token.auth,
            refresh = token.refresh,
            expires = expiresString
        )
    }

    suspend fun getTokenByDeviceId(deviceId: String): AuthToken? {
        val query = queries.getTokenByDeviceId(deviceId)
        return query.executeAsOneOrNull()
    }
}