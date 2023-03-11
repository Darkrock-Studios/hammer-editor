package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.AuthToken
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.utilities.RandomString
import com.soywiz.krypto.sha256
import kotlinx.datetime.Clock
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days

class AccountsRepository(
    private val accountDao: AccountDao,
    private val authTokenDao: AuthTokenDao
) {
    private val tokenLifetime = 30.days

    private val tokenGenerator = RandomString(Token.LENGTH)
    private val saltGenerator = RandomString(5)

    private suspend fun createToken(email: String, deviceId: String): Token {
        val expires = Clock.System.now() + tokenLifetime
        val token = Token(tokenGenerator.nextString(), tokenGenerator.nextString())

        authTokenDao.setToken(
            email = email,
            deviceId = deviceId,
            token = token,
            expires = expires
        )

        return token
    }

    private suspend fun getAuthToken(email: String, deviceId: String): Token {
        val existingToken = authTokenDao.getTokenByDeviceId(deviceId)
        return if (existingToken != null) {
            if (existingToken.isExpired()) {
                // TODO eventually implement token refresh
                createToken(email = email, deviceId = deviceId)
            } else {
                Token(existingToken.token, existingToken.refresh)
            }
        } else {
            createToken(email = email, deviceId = deviceId)
        }
    }

    suspend fun createAccount(email: String, deviceId: String, password: String): Result<Token> {
        val existingAccount = accountDao.findAccount(email)
        return if (existingAccount == null) {
            val salt = saltGenerator.nextString()
            val hashedPassword = hashPassword(password = password, salt = salt)

            accountDao.createAccount(
                email = email,
                salt = salt,
                hashedPassword = hashedPassword
            )

            val token = createToken(email = email, deviceId = deviceId)

            Result.success(token)
        } else {
            Result.failure(CreateFailed("Account already exists"))
        }
    }

    private fun checkPassword(account: Account, plainTextPassword: String): Boolean {
        val hashedPassword = hashPassword(password = plainTextPassword, salt = account.salt)
        return hashedPassword == account.password_hash
    }

    suspend fun login(email: String, password: String, deviceId: String): Result<Token> {
        val account = accountDao.findAccount(email)

        return if (account == null) {
            Result.failure(LoginFailed("Account not found"))
        } else if (!checkPassword(account, password)) {
            Result.failure(LoginFailed("Incorrect password"))
        } else {
            val token = getAuthToken(email, deviceId)
            Result.success(token)
        }
    }

    suspend fun checkToken(token: String): Result<String> {
        val authToken = authTokenDao.getTokenByAuthToken(token)
        return if (authToken != null && !authToken.isExpired()) {
            Result.success(authToken.email)
        } else {
            Result.failure(LoginFailed("No valid token not found"))
        }
    }

    suspend fun refreshToken(deviceId: String, refreshToken: String): Result<Token> {
        val authToken = authTokenDao.getTokenByDeviceId(deviceId)
        return if (authToken != null && authToken.refresh == refreshToken) {
            val newToken = createToken(authToken.email, deviceId)
            Result.success(Token(auth = newToken.auth, refresh = newToken.refresh))
        } else {
            Result.failure(LoginFailed("No valid token not found"))
        }
    }

    companion object {
        fun hashPassword(password: String, salt: String): String {
            val saltedPassword = salt + password
            val hashedPassword = saltedPassword.toByteArray().sha256().toString()
            return hashedPassword
        }
    }
}

class CreateFailed(message: String) : Exception(message)
class LoginFailed(message: String) : Exception(message)

fun AuthToken.isExpired(): Boolean {
    return expires.toInstant() < Clock.System.now()
}