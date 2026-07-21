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
import kotlin.time.Instant

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

	// How long a refresh token outlives its access token. The refresh deadline is
	// expires + this, and since expires slides forward on each issue, an active
	// session never lapses; an idle one must re-login this long after access expiry.
	private val refreshWindow = REFRESH_TOKEN_WINDOW

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
			existingAccount != null -> {
				// Hash anyway so the existing-account path costs the same Argon2 time as
				// creating a new account — no timing oracle for account enumeration.
				hashPassword(password)
				SResult.failure(
					"account already exists",
					Msg.r("api_accounts_create_error_accountexists"),
					CreateFailed("Account already exists")
				)
			}

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

	private fun checkPassword(account: Account, plainTextPassword: String): Boolean =
		checkPassword(plainTextPassword, account.password_hash)

	// Any verify failure (invalid/old hash format) is treated as a wrong password.
	@Suppress("TooGenericExceptionCaught", "SwallowedException")
	private fun checkPassword(plainTextPassword: String, passwordHash: String): Boolean {
		val argon2 = Argon2Factory.create()
		val passwordChars = plainTextPassword.toCharArray()

		return try {
			argon2.verify(passwordHash, passwordChars)
		} catch (_: Exception) {
			// If verification fails (e.g., invalid format, old hash), return false
			false
		} finally {
			argon2.wipeArray(passwordChars)
		}
	}

	// A real Argon2 hash (server-startup params) verified on the unknown-account
	// path so login spends comparable time whether or not the account exists.
	private val decoyPasswordHash: String by lazy { hashPassword(DECOY_PASSWORD) }

	suspend fun login(email: String, password: String, installId: String): SResult<Token> {
		val account = accountDao.findAccount(email)

		val passwordValid = if (account != null) {
			checkPassword(account, password)
		} else {
			// Verify against a decoy hash so a missing account costs the same Argon2
			// time as a wrong password — no timing oracle for account enumeration.
			checkPassword(password, decoyPasswordHash)
			false
		}

		return if (account != null && passwordValid) {
			val token = createToken(account.id, installId)
			SResult.success(token)
		} else {
			// One message for both unknown-account and wrong-password so the
			// response body doesn't reveal whether the account exists.
			SResult.failure("Invalid credentials", Msg.r("api_accounts_login_error_invalid"))
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

	/** The install id bound to [token], or null if the token is unknown. */
	suspend fun getInstallId(token: String): String? {
		val hashedToken = tokenHasher.hashToken(token)
		return authTokenDao.getTokenByAuthToken(hashedToken)?.install_id
	}

	suspend fun refreshToken(userId: Long, installId: String, refreshToken: String): SResult<Token> {
		val hashedRefreshToken = tokenHasher.hashToken(refreshToken)
		val authToken = authTokenDao.getTokenByInstallId(userId, installId)

		return if (authToken != null &&
			authToken.refresh == hashedRefreshToken &&
			!authToken.isRefreshExpired(clock, refreshWindow)
		) {
			val newToken = createToken(userId, installId)
			SResult.success(newToken)
		} else {
			SResult.failure("No valid token not found", Msg.r("api_accounts_login_error_notoken"))
		}
	}

	/**
	 * Delete auth tokens whose refresh window has fully elapsed, i.e. they can no
	 * longer be used to authenticate or to refresh. A still-refreshable row (within
	 * [REFRESH_TOKEN_WINDOW] of its access expiry) is never touched.
	 */
	suspend fun purgeExpiredTokens(now: Instant = clock.now()) {
		authTokenDao.deleteExpiredBefore(now - REFRESH_TOKEN_WINDOW)
	}

	/**
	 * Revoke every session for [email], ending access immediately rather than at the
	 * next token refresh. Returns false if no such account exists.
	 */
	suspend fun forceLogout(email: String): Boolean {
		val account = accountDao.findAccount(email) ?: return false
		authTokenDao.deleteTokensByUserId(account.id)
		return true
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

	suspend fun getAccountOrNull(userId: Long): Account? {
		return accountDao.getAccount(userId)
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

		// A refresh token stays valid until this long past its access token's expiry.
		// The token-maintenance purge uses the same window so it never deletes a row
		// that could still refresh.
		val REFRESH_TOKEN_WINDOW = 180.days

		// Fixed input used only to mint the decoy hash for the login timing defense.
		private const val DECOY_PASSWORD = "hammer-decoy-password"

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
