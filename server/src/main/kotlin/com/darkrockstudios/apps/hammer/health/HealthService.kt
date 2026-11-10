package com.darkrockstudios.apps.hammer.health

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskStatus
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/** The stored health token: only its HMAC, never the plaintext. */
@Serializable
data class HealthToken(
	val hash: String,
	val createdAtMillis: Long,
)

/**
 * Builds the health report served at [HEALTH_ROUTE] and owns the bearer token
 * that unlocks its detail.
 */
class HealthService(
	private val registry: RecurringTaskRegistry,
	private val healthCheckDao: HealthCheckDao,
	private val configRepository: ConfigRepository,
	private val tokenHasher: TokenHasher,
	base64: Base64,
	private val clock: Clock,
) {
	private val logger = LoggerFactory.getLogger(HealthService::class.java)
	private val tokenGenerator = SecureTokenGenerator(TOKEN_BYTES, base64)

	// One computation serves every probe inside the cache window, so several
	// monitors polling together cost a single database ping.
	private val reportLock = Mutex()
	private var cached: HealthReport? = null
	private var cachedAt: Instant? = null

	suspend fun report(): HealthReport = reportLock.withLock {
		val now = clock.now()
		val last = cached
		val lastAt = cachedAt
		if (last != null && lastAt != null && now - lastAt < CACHE_TTL) return@withLock last

		buildReport(now).also {
			cached = it
			cachedAt = now
		}
	}

	private suspend fun buildReport(now: Instant): HealthReport {
		val database = checkDatabase()
		val jobs = jobsBySlug(registry.statuses()).mapValues { (_, status) -> status.health(now) }
		val ok = database.ok && jobs.values.all { it.ok }
		return HealthReport(
			ok = ok,
			status = if (ok) OverallState.OK else OverallState.FAILING,
			checkedAt = now.toString(),
			database = database,
			jobs = jobs,
		)
	}

	private suspend fun checkDatabase(): DatabaseHealth {
		val start = TimeSource.Monotonic.markNow()
		return try {
			if (healthCheckDao.ping(DB_TIMEOUT)) {
				DatabaseHealth(DatabaseState.OK, start.elapsedNow().inWholeMilliseconds)
			} else {
				DatabaseHealth(DatabaseState.TIMEOUT, null)
			}
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			logger.warn("Health check database ping failed", e)
			DatabaseHealth(DatabaseState.DOWN, null)
		}
	}

	suspend fun generateToken(): String {
		val token = tokenGenerator.generateToken()
		configRepository.set(
			AdminServerConfig.HEALTH_TOKEN,
			HealthToken(tokenHasher.hashToken(token), clock.now().toEpochMilliseconds()),
		)
		return token
	}

	suspend fun revokeToken() {
		configRepository.set(AdminServerConfig.HEALTH_TOKEN, null)
	}

	suspend fun tokenCreatedAt(): Instant? =
		configRepository.get(AdminServerConfig.HEALTH_TOKEN)
			?.let { Instant.fromEpochMilliseconds(it.createdAtMillis) }

	suspend fun verifyToken(given: String): Boolean {
		val stored = configRepository.get(AdminServerConfig.HEALTH_TOKEN) ?: return false
		val givenHash = tokenHasher.hashToken(given)
		return MessageDigest.isEqual(
			givenHash.toByteArray(Charsets.UTF_8),
			stored.hash.toByteArray(Charsets.UTF_8),
		)
	}

	companion object {
		private const val TOKEN_BYTES = 32
		private val CACHE_TTL = 5.seconds
		private val DB_TIMEOUT = 3.seconds

		/** How far past its scheduled run a job may be before it counts as overdue. */
		val OVERDUE_GRACE = 10.minutes

		/** "Account deletion job" -> "account-deletion". */
		fun slugOf(taskName: String): String =
			taskName.trim()
				.lowercase()
				.removeSuffix(" job")
				.replace(Regex("[^a-z0-9]+"), "-")
				.trim('-')

		/** Keys each status by its slug; a colliding slug gets a numeric suffix. */
		fun jobsBySlug(statuses: List<RecurringTaskStatus>): Map<String, RecurringTaskStatus> {
			val result = LinkedHashMap<String, RecurringTaskStatus>()
			for (status in statuses) {
				val base = slugOf(status.name).ifEmpty { "job" }
				var slug = base
				var n = 2
				while (slug in result) slug = "$base-${n++}"
				result[slug] = status
			}
			return result
		}

		fun RecurringTaskStatus.health(now: Instant): JobHealth {
			val state = when {
				!running -> JobState.STOPPED
				lastTickFailed -> JobState.FAILED
				isOverdue(now) -> JobState.OVERDUE
				else -> JobState.OK
			}
			return JobHealth(
				status = state,
				lastRun = lastRun?.toString(),
				nextRun = nextRun?.toString(),
				error = lastError.takeIf { lastTickFailed },
			)
		}

		// While a tick is in flight nextRun still holds the time that tick was due, and
		// before the first tick finishes it is null, so fall back to when that tick started.
		private fun RecurringTaskStatus.isOverdue(now: Instant): Boolean {
			val due = nextRun ?: lastRun ?: return false
			return now > due + OVERDUE_GRACE
		}
	}
}
