package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class AuthTokenDaoTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase
	private lateinit var dao: AuthTokenDao

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SqliteTestDatabase()
		db.initialize()
		dao = AuthTokenDao(db)
		setupKoin()
	}

	@Test
	fun `deleteExpiredBefore removes rows expiring before the cutoff and keeps the rest`() = runTest {
		val now = Clock.System.now()
		dao.setToken(1L, "stale", Token(1L, "auth1", "refresh1"), expires = now + 1.days)
		dao.setToken(2L, "live", Token(2L, "auth2", "refresh2"), expires = now + 100.days)

		dao.deleteExpiredBefore(now + 10.days)

		assertNull(dao.getTokenByInstallId(1L, "stale"))
		assertNotNull(dao.getTokenByInstallId(2L, "live"))
	}
}
