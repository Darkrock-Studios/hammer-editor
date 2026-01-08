package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.GetAccountsPaginated
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import de.mkammerer.argon2.Argon2Factory
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Clock
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
				Msg.r("api_accounts_create_error_accountexists"),
				CreateFailed("Account already exists")
			)

			!validateEmail(email) -> SResult.failure(
				"invalid email",
				Msg.r("api_accounts_create_error_invalidemail"),
				CreateFailed("Invalid email")
			)

			passwordResult != PasswordResult.VALID -> SResult.failure(
				"password failure",
				InvalidPassword.getMessage(passwordResult),
				InvalidPassword(passwordResult)
			)

			else -> {
				val hashedPassword = hashPassword(password = password)
				val cipherSalt = cipherSaltGenerator.generateToken()

				// First account on the server is automatically Admin
				val numAccounts = accountDao.numAccounts()
				val isAdmin = (numAccounts == 0L)

				val userId = accountDao.createAccount(
					email = email,
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
		val argon2 = Argon2Factory.create()
		val passwordChars = plainTextPassword.toCharArray()

		return try {
			argon2.verify(account.password_hash, passwordChars)
		} catch (e: Exception) {
			// If verification fails (e.g., invalid format, old hash), return false
			false
		} finally {
			argon2.wipeArray(passwordChars)
		}
	}

	suspend fun login(email: String, password: String, installId: String): SResult<Token> {
		val account = accountDao.findAccount(email)

		return if (account == null) {
			SResult.failure("Account not found", Msg.r("api_accounts_login_error_notfound"))
		} else if (!checkPassword(account, password)) {
			SResult.failure("Incorrect password", Msg.r("api_accounts_login_error_badpassword"))
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
			SResult.failure("No valid token not found", Msg.r("api_accounts_login_error_notoken"))
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
			SResult.failure("No valid token not found", Msg.r("api_accounts_login_error_notoken"))
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

	suspend fun updatePenName(userId: Long, penName: String?) {
		accountDao.updatePenName(userId, penName?.trim())
	}

	suspend fun isPenNameAvailable(penName: String, excludeUserId: Long? = null): Boolean {
		return accountDao.isPenNameAvailable(penName.trim(), excludeUserId)
	}

	suspend fun findAccountByPenName(penName: String): Account? {
		return accountDao.findAccountByPenName(penName)
	}

	suspend fun updateBio(userId: Long, bio: String?) {
		accountDao.updateBio(userId, bio?.trim())
	}

	suspend fun getBio(userId: Long): String? {
		return accountDao.getBio(userId)
	}

	suspend fun numAccounts(): Long {
		return accountDao.numAccounts()
	}

	suspend fun getAccountsPaginated(page: Int, pageSize: Int): List<GetAccountsPaginated> {
		return accountDao.getAccountsPaginated(page, pageSize)
	}

	companion object {
		const val MIN_PASSWORD_LENGTH = 8
		const val MAX_PASSWORD_LENGTH = 64
		const val CIPHER_SALT_LENGTH = 16

		// Argon2 parameters
		const val ARGON2_MEMORY_COST_KIB = 65536  // 64 MiB
		const val ARGON2_TIME_COST = 2  // iterations
		const val ARGON2_PARALLELISM = 1  // threads

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

		fun hashPassword(password: String): String {
			val argon2 = Argon2Factory.create()
			val passwordChars = password.toCharArray()

			try {
				return argon2.hash(
					ARGON2_TIME_COST,
					ARGON2_MEMORY_COST_KIB,
					ARGON2_PARALLELISM,
					passwordChars
				)
			} finally {
				argon2.wipeArray(passwordChars)
			}
		}

		fun validateEmail(email: String): Boolean {
			val trimmedInput = email.trim()
			return emailPattern.matches(trimmedInput)
		}

		fun validatePassword(password: String): PasswordResult {
			val trimmedInput = password.trim()
			return when {
				trimmedInput.length < MIN_PASSWORD_LENGTH -> PasswordResult.TOO_SHORT
				trimmedInput.length > MAX_PASSWORD_LENGTH -> PasswordResult.TOO_LONG
				else -> PasswordResult.VALID
			}
		}
	}
}
