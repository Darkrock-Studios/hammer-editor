package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.AuthToken
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.utilities.RandomString
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.soywiz.krypto.sha256
import kotlinx.datetime.Clock
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days

class AccountsRepository(
    private val accountDao: AccountDao,
    private val authTokenDao: AuthTokenDao
) {
    private val tokenLifetime = 30.days

    private val tokenGenerator = SecureTokenGenerator(Token.LENGTH)
    private val saltGenerator = RandomString(5)

    private suspend fun createToken(userId: Long, installId: String): Token {
        val expires = Clock.System.now() + tokenLifetime
        val token = Token(
            userId = userId,
            auth = tokenGenerator.generateToken(),
            refresh = tokenGenerator.generateToken()
        )

        authTokenDao.setToken(
            userId = userId,
            installId = installId,
            token = token,
            expires = expires
        )

        return token
    }

    private suspend fun getAuthToken(userId: Long, installId: String): Token {
        val existingToken = authTokenDao.getTokenByInstallId(installId)
		return if (existingToken != null) {
			if (existingToken.isExpired()) {
				createToken(userId = userId, installId = installId)
			} else {
				Token(existingToken.userId, existingToken.token, existingToken.refresh)
			}
		} else {
			createToken(userId = userId, installId = installId)
		}
	}

	suspend fun hasUsers(): Boolean = accountDao.numAccounts() > 0

	suspend fun createAccount(email: String, installId: String, password: String): Result<Token> {
		val existingAccount = accountDao.findAccount(email)
		val passwordResult = validatePassword(password)
		return when {
			existingAccount != null -> Result.failure(CreateFailed("Account already exists"))
			!validateEmail(email) -> Result.failure(CreateFailed("Invalid email"))
			passwordResult != PasswordResult.VALID -> Result.failure(InvalidPassword(passwordResult))
			else -> {
				val salt = saltGenerator.nextString()
				val hashedPassword = hashPassword(password = password, salt = salt)

				// First account on the server is automatically Admin
				val numAccounts = accountDao.numAccounts()
				val isAdmin = (numAccounts == 0L)

				val userId = accountDao.createAccount(
					email = email,
					salt = salt,
					hashedPassword = hashedPassword,
					isAdmin = isAdmin
				)

				val token = createToken(userId = userId, installId = installId)

                Result.success(token)
            }
        }
    }

    private fun checkPassword(account: Account, plainTextPassword: String): Boolean {
        val hashedPassword = hashPassword(password = plainTextPassword, salt = account.salt)
        return hashedPassword == account.password_hash
    }

    suspend fun login(email: String, password: String, installId: String): Result<Token> {
        val account = accountDao.findAccount(email)

        return if (account == null) {
            Result.failure(LoginFailed("Account not found"))
        } else if (!checkPassword(account, password)) {
            Result.failure(LoginFailed("Incorrect password"))
        } else {
            val token = getAuthToken(account.id, installId)
            Result.success(token)
        }
    }

    suspend fun checkToken(userId: Long, token: String): Result<Long> {
        val authToken = authTokenDao.getTokenByAuthToken(token)

        return if (authToken != null && authToken.userId == userId && !authToken.isExpired()) {
            Result.success(authToken.userId)
        } else {
            Result.failure(LoginFailed("No valid token not found"))
        }
    }

    suspend fun refreshToken(userId: Long, installId: String, refreshToken: String): Result<Token> {
        val authToken = authTokenDao.getTokenByInstallId(installId)
        return if (authToken != null && authToken.refresh == refreshToken) {
            val newToken = createToken(userId, installId)
            Result.success(
                Token(
                    userId = userId,
                    auth = newToken.auth,
                    refresh = newToken.refresh
                )
            )
        } else {
            Result.failure(LoginFailed("No valid token not found"))
        }
    }

	fun validatePassword(password: String): PasswordResult {
		val trimmedInput = password.trim()
		return when {
			trimmedInput.length < MIN_PASSWORD_LENGTH -> PasswordResult.TOO_SHORT
			trimmedInput.length > MAX_PASSWORD_LENGTH -> PasswordResult.TOO_LONG
			else -> PasswordResult.VALID
		}
	}

	suspend fun isAdmin(userId: Long): Boolean {
		return accountDao.getAccount(userId)?.isAdmin == true
	}

	suspend fun findAccount(email: String): Account? {
		return accountDao.findAccount(email)
	}

	suspend fun getAccount(userId: Long): Account {
		return accountDao.getAccount(userId) ?: throw AccountNotFound(userId)
	}

	companion object {
		const val MIN_PASSWORD_LENGTH = 8
		const val MAX_PASSWORD_LENGTH = 64

		// TODO: (?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])
		private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@(.+)$")

		enum class PasswordResult {
			VALID,
			TOO_SHORT,
			TOO_LONG,
			NO_UPPERCASE,
			NO_LOWERCASE,
			NO_NUMBER,
			NO_SPECIAL
		}

		fun hashPassword(password: String, salt: String): String {
			val saltedPassword = salt + password
			val hashedPassword = saltedPassword.toByteArray().sha256().toString()
			return hashedPassword
		}

		fun validateEmail(email: String): Boolean {
			val trimmedInput = email.trim()
			return emailPattern.matches(trimmedInput)
		}
	}
}

open class CreateFailed(message: String) : Exception(message)
class InvalidPassword(val result: AccountsRepository.Companion.PasswordResult) : CreateFailed(getMessage(result)) {
    companion object {
        private fun getMessage(result: AccountsRepository.Companion.PasswordResult) = when (result) {
            AccountsRepository.Companion.PasswordResult.TOO_SHORT -> "Password too short"
            AccountsRepository.Companion.PasswordResult.TOO_LONG -> "Password too long"
            AccountsRepository.Companion.PasswordResult.NO_UPPERCASE -> "Password must contain at least one uppercase letter"
			AccountsRepository.Companion.PasswordResult.NO_LOWERCASE -> "Password must contain at least one lowercase letter"
			AccountsRepository.Companion.PasswordResult.NO_NUMBER -> "Password must contain at least one number"
			AccountsRepository.Companion.PasswordResult.NO_SPECIAL -> "Password must contain at least one special character"
			else -> "Invalid password"
		}
	}
}

class LoginFailed(message: String) : Exception(message)

class AccountNotFound(userId: Long) : Exception("User ID ($userId) not found")


fun AuthToken.isExpired(): Boolean {
	return expires.toInstant() < Clock.System.now()
}