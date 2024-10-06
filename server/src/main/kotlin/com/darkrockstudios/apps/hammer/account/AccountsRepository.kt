package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.RandomString
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import korlibs.crypto.sha256
import kotlinx.datetime.Clock
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.days

class AccountsRepository(
	private val accountDao: AccountDao,
	private val authTokenDao: AuthTokenDao,
	private val clock: Clock,
	secureRandom: SecureRandom,
	base64: Base64,
) {
	private val tokenLifetime = 30.days

	private val authTokenGenerator = SecureTokenGenerator(Token.LENGTH, base64)
	private val cipherSaltGenerator = SecureTokenGenerator(CIPHER_SALT_LENGTH, base64)
	private val saltGenerator = RandomString(PASSWORD_SALT_LENGTH, secureRandom)

	private suspend fun createToken(userId: Long, installId: String): Token {
		val expires = clock.now() + tokenLifetime
		val token = Token(
			userId = userId,
			auth = authTokenGenerator.generateToken(),
			refresh = authTokenGenerator.generateToken()
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
		val existingToken = authTokenDao.getTokenByInstallId(userId, installId)
		return if (existingToken != null) {
			if (existingToken.user_id != userId) {
				error("Existing Token returned for installId `$installId` was for user: ${existingToken.user_id} instead of user: $userId")
			} else if (existingToken.isExpired(clock)) {
				createToken(userId = userId, installId = installId)
			} else {
				Token(
					userId = existingToken.user_id,
					auth = existingToken.token,
					refresh = existingToken.refresh
				)
			}
		} else {
			createToken(userId = userId, installId = installId)
		}
	}

	suspend fun hasUsers(): Boolean = accountDao.numAccounts() > 0

	suspend fun createAccount(email: String, installId: String, password: String): ServerResult<Token> {
		val existingAccount = accountDao.findAccount(email)
		val passwordResult = validatePassword(password)
		return when {
			existingAccount != null -> SResult.failure(
				"account already exists",
				Msg.r("api.accounts.create.error.accountexists"),
				CreateFailed("Account already exists")
			)

			!validateEmail(email) -> SResult.failure(
				"invalid email",
				Msg.r("api.accounts.create.error.invalidemail"),
				CreateFailed("Invalid email")
			)

			passwordResult != PasswordResult.VALID -> SResult.failure(
				"password failure",
				InvalidPassword.getMessage(passwordResult),
				InvalidPassword(passwordResult)
			)

			else -> {
				val salt = saltGenerator.nextString()
				val hashedPassword = hashPassword(password = password, salt = salt)
				val cipherSalt = cipherSaltGenerator.generateToken()

				// First account on the server is automatically Admin
				val numAccounts = accountDao.numAccounts()
				val isAdmin = (numAccounts == 0L)

				val userId = accountDao.createAccount(
					email = email,
					salt = salt,
					hashedPassword = hashedPassword,
					cipherSecret = cipherSalt,
					isAdmin = isAdmin
				)

				val token = createToken(userId = userId, installId = installId)

				SResult.success(token)
			}
		}
	}

	private fun checkPassword(account: Account, plainTextPassword: String): Boolean {
		val hashedPassword = hashPassword(password = plainTextPassword, salt = account.salt)
		return hashedPassword == account.password_hash
	}

	suspend fun login(email: String, password: String, installId: String): SResult<Token> {
		val account = accountDao.findAccount(email)

		return if (account == null) {
			SResult.failure("Account not found", Msg.r("api.accounts.login.error.notfound"))
		} else if (!checkPassword(account, password)) {
			SResult.failure("Incorrect password", Msg.r("api.accounts.login.error.badpassword"))
		} else {
			val token = getAuthToken(account.id, installId)
			SResult.success(token)
		}
	}

	suspend fun checkToken(userId: Long, token: String): SResult<Long> {
		val authToken = authTokenDao.getTokenByAuthToken(token)

		return if (authToken != null && authToken.user_id == userId && !authToken.isExpired(clock)) {
			SResult.success(authToken.user_id)
		} else {
			SResult.failure("No valid token not found", Msg.r("api.accounts.login.error.notoken"))
		}
	}

	suspend fun refreshToken(userId: Long, installId: String, refreshToken: String): SResult<Token> {
		val authToken = authTokenDao.getTokenByInstallId(userId, installId)
		return if (authToken != null && authToken.refresh == refreshToken) {
			val newToken = createToken(userId, installId)
			SResult.success(
				Token(
					userId = userId,
					auth = newToken.auth,
					refresh = newToken.refresh
				)
			)
		} else {
			SResult.failure("No valid token not found", Msg.r("api.accounts.login.error.notoken"))
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
		return accountDao.getAccount(userId)?.is_admin == true
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
		const val PASSWORD_SALT_LENGTH = 8
		const val CIPHER_SALT_LENGTH = 16

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
