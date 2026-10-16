package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.account.mfa.MfaTestFixture
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.ktor.util.logging.KtorSimpleLogger
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class TokenMaintenanceJobTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var mfa: MfaTestFixture

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		db = SharedPostgresTestDatabase()
		db.initialize()
		mfa = MfaTestFixture(db)
	}

	@Test
	fun `Tick purges expired MFA challenges`() = runTest {
		val userId = mfa.newAccount()
		mfa.enroll(userId)
		val expired = mfa.repository.createChallenge(userId, "install-a")
		val issuedAt = mfa.now
		mfa.now += 10.minutes
		val live = mfa.repository.createChallenge(userId, "install-b")
		val job = TokenMaintenanceJob(
			AccountsRepository(mfa.accountDao, AuthTokenDao(db), mfa.clock, mfa.tokenHasher, Base64.Default),
			mockk(relaxed = true),
			mfa.repository,
			KtorSimpleLogger("test"),
		)

		job.tick()

		assertNull(mfa.challengeDao.claimAttempt(mfa.tokenHasher.hashToken(expired.token), "install-a", issuedAt, 5))
		assertNotNull(mfa.challengeDao.claimAttempt(mfa.tokenHasher.hashToken(live.token), "install-b", mfa.now, 5))
	}
}
