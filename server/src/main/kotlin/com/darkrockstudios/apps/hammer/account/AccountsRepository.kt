package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.GetAccountsPaginatedSortByCreated
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.validate.EmailValidator
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.CommunityAuthor
import com.darkrockstudios.apps.hammer.utilities.*
import de.mkammerer.argon2.Argon2Factory
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

enum class UserSortField(val value: String) {
	CREATED("created"),
	LAST_SYNC("lastsync"),
	PROJECT_COUNT("projectcount");

	companion object {
		fun fromString(value: String): UserSortField {
			return entries.find { it.value.equals(value, ignoreCase = true) } ?: CREATED
		}
	}
}

enum class SortDirection(val value: String) {
	ASCENDING("asc"),
	DESCENDING("desc");

	companion object {
		fun fromString(value: String): SortDirection {
			return entries.find { it.value.equals(value, ignoreCase = true) } ?: DESCENDING
		}
	}
}

class AccountsRepository(
	private val accountDao: AccountDao,
	private val authTokenDao: AuthTokenDao,
	private val clock: Clock,
	private val tokenHasher: TokenHasher,
	base64: Base64,
) {
	private val tokenLifetime = 30.days

	private val authTokenGenerator = SecureTokenGenerator(Token.LENGTH, base64)
	private val cipherSaltGenerator = SecureTokenGenerator(CIPHER_SALT_LENGTH, base64)

	private suspend fun createToken(userId: Long, installId: String): Token {
		val expires = clock.now() + tokenLifetime

		val plainAuthToken = authTokenGenerator.generateToken()
		val plainRefreshToken = authTokenGenerator.generateToken()

		val hashedAuthToken = tokenHasher.hashToken(plainAuthToken)
		val hashedRefreshToken = tokenHasher.hashToken(plainRefreshToken)

		val hashedToken = Token(
			userId = userId,
			auth = hashedAuthToken,
			refresh = hashedRefreshToken
		)

		authTokenDao.setToken(
			userId = userId,
			installId = installId,
			token = hashedToken,
			expires = expires
		)

		return Token(
			userId = userId,
			auth = plainAuthToken,
			refresh = plainRefreshToken
		)
	}

	suspend fun hasUsers(): Boolean = accountDao.numAccounts() > 0

	suspend fun createAccount(email: String, installId: String, password: String): ServerResult<Token> {
		val existingAccount = accountDao.findAccount(email)
		val passwordResult = PasswordValidator.validate(password)
		return when {
			existingAccount != null -> SResult.failure(
				"account already exists",
				Msg.r("api_accounts_create_error_accountexists"),
				CreateFailed("Account already exists")
			)

			!EmailValidator.validate(email) -> SResult.failure(
				"invalid email",
				Msg.r("api_accounts_create_error_invalidemail"),
				CreateFailed("Invalid email")
			)

			passwordResult != PasswordValidationResult.VALID -> SResult.failure(
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
			val token = createToken(account.id, installId)
			SResult.success(token)
		}
	}

	suspend fun checkToken(userId: Long, token: String): SResult<Long> {
		val hashedToken = tokenHasher.hashToken(token)
		val authToken = authTokenDao.getTokenByAuthToken(hashedToken)

		return if (authToken != null && authToken.user_id == userId && !authToken.isExpired(clock)) {
			SResult.success(authToken.user_id)
		} else {
			SResult.failure("No valid token not found", Msg.r("api_accounts_login_error_notoken"))
		}
	}

	suspend fun refreshToken(userId: Long, installId: String, refreshToken: String): SResult<Token> {
		val hashedRefreshToken = tokenHasher.hashToken(refreshToken)
		val authToken = authTokenDao.getTokenByInstallId(userId, installId)

		return if (authToken != null && authToken.refresh == hashedRefreshToken) {
			val newToken = createToken(userId, installId)
			SResult.success(newToken)
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

	suspend fun getAccountsPaginated(
		page: Int,
		pageSize: Int,
		sortBy: UserSortField = UserSortField.CREATED,
		sortDirection: SortDirection = SortDirection.DESCENDING
	): List<GetAccountsPaginatedSortByCreated> {
		return accountDao.getAccountsPaginated(page, pageSize, sortBy, sortDirection)
	}

	suspend fun updateCommunityMember(userId: Long, isCommunityMember: Boolean) {
		accountDao.updateCommunityMember(userId, isCommunityMember)
	}

	suspend fun getCommunityMember(userId: Long): Boolean {
		return accountDao.getCommunityMember(userId)
	}

	suspend fun getCommunityAuthors(page: Int, pageSize: Int): List<CommunityAuthor> {
		return accountDao.getCommunityAuthors(page, pageSize)
	}

	suspend fun countCommunityAuthors(): Long {
		return accountDao.countCommunityAuthors()
	}

	companion object {
		const val CIPHER_SALT_LENGTH = 16

		// Argon2 parameters
		const val ARGON2_MEMORY_COST_KIB = 65536  // 64 MiB
		const val ARGON2_TIME_COST = 3  // iterations
		const val ARGON2_PARALLELISM = 2  // threads

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
	}
}
