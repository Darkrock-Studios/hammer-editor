package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonitoringConfigTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var dao: ServerConfigDao

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		dao = ServerConfigDao(db)

		setupKoin()
	}

	private fun createRepo() = ConfigRepository(dao)

	@Test
	fun `returns sane defaults when unset`() = runTest {
		val config = createRepo().get(AdminServerConfig.MONITORING_CONFIG)

		assertEquals(MonitoringConfig(), config)
		// The decisions we care about most:
		assertTrue(config.enabled, "collection on by default")
		assertFalse(config.alertEmailEnabled, "alerting off by default")
		assertEquals(30, config.metricsRetentionDays)
	}

	@Test
	fun `round-trips through the config repository`() = runTest {
		val repo = createRepo()
		val updated = MonitoringConfig(
			enabled = false,
			trackApiMetrics = false,
			storeLoginIp = false,
			metricsRetentionDays = 7,
			errorRetentionDays = 14,
			loginAttemptRetentionDays = 3,
			alertEmailEnabled = true,
			alertEmail = "admin@example.com",
			syncFailureThreshold = 9,
		)
		repo.set(AdminServerConfig.MONITORING_CONFIG, updated)

		assertEquals(updated, repo.get(AdminServerConfig.MONITORING_CONFIG))
	}
}
