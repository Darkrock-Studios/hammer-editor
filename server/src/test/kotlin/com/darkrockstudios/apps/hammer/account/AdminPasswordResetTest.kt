package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.PasswordResetTokenDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utilities.Argon2PasswordHasher
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * The admin-only CLI reset, run against real DAOs so the is_admin rule is
 * checked where it is enforced.
 */
class AdminPasswordResetTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var authTokenDao: AuthTokenDao

	private val oldHash = AccountsRepository.hashPassword("OldPassword1")
	private val newPassword = "qweasdZXC123"

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		accountDao = AccountDao(db)
		authTokenDao = AuthTokenDao(db)
		setupKoin()
	}

	private fun repository() = PasswordResetRepository(
		accountDao = accountDao,
		passwordResetTokenDao = PasswordResetTokenDao(db),
		authTokenDao = authTokenDao,
		clock = Clock.System,
		base64 = createTokenBase64(),
	)

	private suspend fun createAccount(email: String, isAdmin: Boolean): Long =
		accountDao.createAccount(email, oldHash, "secret", isAdmin)

	private suspend fun passwordIs(userId: Long, password: String): Boolean =
		Argon2PasswordHasher.verify(assertNotNull(accountDao.getAccount(userId)).password_hash, password.toCharArray())

	@Test
	fun `resets the only admin without an email and signs out its devices`() = runTest {
		val adminId = createAccount("admin@example.com", isAdmin = true)
		val writerId = createAccount("writer@example.com", isAdmin = false)
		authTokenDao.setToken(adminId, "install-1", Token(adminId, "auth-admin", "refresh-admin"), Clock.System.now() + 30.days)
		authTokenDao.setToken(writerId, "install-2", Token(writerId, "auth-writer", "refresh-writer"), Clock.System.now() + 30.days)

		val result = repository().resetAdminPassword(email = null, newPassword = newPassword)

		assertEquals(AdminPasswordResetResult.Success("admin@example.com"), result)
		assertTrue(passwordIs(adminId, newPassword))
		assertTrue(passwordIs(writerId, "OldPassword1"))
		assertNull(authTokenDao.getTokenByAuthToken("auth-admin"))
		assertNotNull(authTokenDao.getTokenByAuthToken("auth-writer"))
	}

	@Test
	fun `reports when the server has no admin`() = runTest {
		createAccount("writer@example.com", isAdmin = false)

		val result = repository().resetAdminPassword(email = null, newPassword = newPassword)

		assertIs<AdminPasswordResetResult.NoAdmin>(result)
	}

	@Test
	fun `with several admins and no email it lists them and changes nothing`() = runTest {
		val firstId = createAccount("first@example.com", isAdmin = true)
		val secondId = createAccount("second@example.com", isAdmin = true)

		val result = repository().resetAdminPassword(email = null, newPassword = newPassword)

		assertEquals(
			AdminPasswordResetResult.MultipleAdmins(listOf("first@example.com", "second@example.com")),
			result,
		)
		assertTrue(passwordIs(firstId, "OldPassword1"))
		assertTrue(passwordIs(secondId, "OldPassword1"))
	}

	@Test
	fun `an email picks one admin among several`() = runTest {
		val firstId = createAccount("first@example.com", isAdmin = true)
		val secondId = createAccount("second@example.com", isAdmin = true)

		val result = repository().resetAdminPassword(email = "Second@Example.com", newPassword = newPassword)

		assertEquals(AdminPasswordResetResult.Success("second@example.com"), result)
		assertTrue(passwordIs(firstId, "OldPassword1"))
		assertTrue(passwordIs(secondId, newPassword))
	}

	@Test
	fun `refuses an email that is not an admin`() = runTest {
		createAccount("admin@example.com", isAdmin = true)
		val writerId = createAccount("writer@example.com", isAdmin = false)

		val result = repository().resetAdminPassword(email = "writer@example.com", newPassword = newPassword)

		assertEquals(AdminPasswordResetResult.NotAdmin("writer@example.com"), result)
		assertTrue(passwordIs(writerId, "OldPassword1"))
	}

	@Test
	fun `refuses an email with no account`() = runTest {
		createAccount("admin@example.com", isAdmin = true)

		val result = repository().resetAdminPassword(email = "nobody@example.com", newPassword = newPassword)

		assertEquals(AdminPasswordResetResult.NoSuchAccount("nobody@example.com"), result)
	}

	@Test
	fun `rejects a password that fails validation without touching the account`() = runTest {
		val adminId = createAccount("admin@example.com", isAdmin = true)
		authTokenDao.setToken(adminId, "install-1", Token(adminId, "auth-admin", "refresh-admin"), Clock.System.now() + 30.days)

		val result = repository().resetAdminPassword(email = null, newPassword = "short")

		assertEquals(AdminPasswordResetResult.PasswordRejected(PasswordValidationResult.TOO_SHORT), result)
		assertTrue(passwordIs(adminId, "OldPassword1"))
		assertNotNull(authTokenDao.getTokenByAuthToken("auth-admin"))
	}
}
