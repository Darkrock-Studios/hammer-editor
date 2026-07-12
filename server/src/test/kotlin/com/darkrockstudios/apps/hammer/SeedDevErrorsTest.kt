package com.darkrockstudios.apps.hammer

import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.time.OffsetDateTime

// Scratch seeder: inserts fabricated error groups into the RUNNING dev server's
// embedded Postgres (localhost:54329) so the monitoring errors UI can be exercised.
class SeedDevErrorsTest {

	private data class Seed(
		val type: String,
		val route: String?,
		val userId: Long?,
		val message: String?,
		val stack: String?,
		val status: Int,
		val count: Long,
		val hoursAgo: Long,
	)

	@Test
	fun `seed dev database with fabricated errors`() {
		val now = OffsetDateTime.now()
		val seeds = listOf(
			// Scraper noise: 426 warnings across junk routes.
			Seed(
				"UnsupportedProtocolVersionException",
				"/api/.env",
				null,
				"Unsupported protocol version: null",
				null,
				426,
				54,
				2
			),
			Seed(
				"UnsupportedProtocolVersionException",
				"/api/",
				null,
				"Unsupported protocol version: null",
				null,
				426,
				6,
				7
			),
			Seed(
				"UnsupportedProtocolVersionException",
				"/api/v2/static/not.found",
				null,
				"Unsupported protocol version: null",
				null,
				426,
				26,
				20
			),
			Seed(
				"UnsupportedProtocolVersionException",
				"/api/version",
				null,
				"Unsupported protocol version: null",
				null,
				426,
				2,
				27
			),
			Seed(
				"UnsupportedProtocolVersionException",
				"/api/default",
				null,
				"Unsupported protocol version: null",
				null,
				426,
				1,
				39
			),
			Seed(
				"UnsupportedProtocolVersionException",
				"/api/.env/leafmailer2.8",
				null,
				"Unsupported protocol version: null",
				null,
				426,
				1,
				52
			),
			Seed(
				"UnsupportedProtocolVersionException",
				"/api/.env/revision",
				null,
				"Unsupported protocol version: null",
				null,
				426,
				1,
				52
			),
			Seed(
				"UnsupportedProtocolVersionException",
				"/api/.env/wp-admin/js/widgets/cloud",
				null,
				"Unsupported protocol version: null",
				null,
				426,
				1,
				52
			),
			// A different 4xx warning type.
			Seed(
				"InvalidSyncIdException",
				"/api/projects/{id}/upload_entity/{entityId}",
				2L,
				"Invalid sync id",
				"com.darkrockstudios.apps.hammer.plugins.InvalidSyncIdException: Invalid sync id\n\tat com.darkrockstudios.apps.hammer.project.ProjectRepository.uploadEntity(ProjectRepository.kt:88)",
				400,
				3,
				30,
			),
			// Real 5xx errors with stack traces.
			Seed(
				"RuntimeException",
				"/api/projects/{id}/begin_sync",
				2L,
				"Sync session collision",
				"java.lang.RuntimeException: Sync session collision\n\tat com.darkrockstudios.apps.hammer.project.ProjectRepository.beginProjectSync(ProjectRepository.kt:112)\n\tat com.darkrockstudios.apps.hammer.plugins.ProjectRoutesKt\$projectRoutes\$1.invokeSuspend(ProjectRoutes.kt:61)\n\tat io.ktor.server.routing.Routing.interceptor(Routing.kt:167)",
				500,
				11,
				5,
			),
			Seed(
				"NullPointerException",
				"/api/account/refresh_token/{userId}",
				3L,
				"null cannot be cast to non-null type kotlin.String",
				"java.lang.NullPointerException: null cannot be cast to non-null type kotlin.String\n\tat com.darkrockstudios.apps.hammer.account.AccountsRepository.refreshToken(AccountsRepository.kt:141)",
				500,
				2,
				12,
			),
			Seed(
				"SerializationException",
				"/api/projects/{id}/download_entity/{entityId}",
				1L,
				"Unexpected JSON token at offset 214",
				"kotlinx.serialization.json.internal.JsonDecodingException: Unexpected JSON token at offset 214\n\tat kotlinx.serialization.json.internal.AbstractJsonLexer.fail(AbstractJsonLexer.kt:598)",
				500,
				4,
				49,
			),
			Seed(
				"SocketTimeoutException",
				"/api/media/upload",
				2L,
				"Read timed out",
				"java.net.SocketTimeoutException: Read timed out\n\tat java.base/sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:288)",
				503,
				1,
				64,
			),
		)

		DriverManager.getConnection("jdbc:postgresql://localhost:54329/postgres", "postgres", "")
			.use { conn ->
				val sql = """
				INSERT INTO error_log(
					fingerprint, exception_type, route, user_id, message, stack_trace, status,
					occurrence_count, first_seen, last_seen
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (fingerprint) DO UPDATE SET
					occurrence_count = EXCLUDED.occurrence_count,
					last_seen = EXCLUDED.last_seen,
					message = EXCLUDED.message,
					stack_trace = EXCLUDED.stack_trace,
					status = EXCLUDED.status
			""".trimIndent()
				conn.prepareStatement(sql).use { stmt ->
					for (s in seeds) {
						val lastSeen = now.minusHours(s.hoursAgo)
						stmt.setString(1, "${s.type}|${s.route ?: ""}|${s.userId ?: ""}")
						stmt.setString(2, s.type)
						stmt.setString(3, s.route)
						if (s.userId != null) stmt.setLong(4, s.userId) else stmt.setNull(
							4,
							java.sql.Types.BIGINT
						)
						stmt.setString(5, s.message)
						stmt.setString(6, s.stack)
						stmt.setInt(7, s.status)
						stmt.setLong(8, s.count)
						stmt.setObject(9, lastSeen.minusDays(3))
						stmt.setObject(10, lastSeen)
						stmt.addBatch()
					}
					val results = stmt.executeBatch()
					println("### Seeded ${results.size} error groups into dev error_log ###")
				}
			}
	}
}
