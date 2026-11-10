package com.darkrockstudios.apps.hammer.health

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskStatus
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.sha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * The stored health token: only its SHA-256, never the plaintext. The token is 256
 * random bits, so an unkeyed hash is enough and it survives tokenHmac key rotation.
 */
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

	// This service is the only writer of the token, so after the first read it is served
	// from memory. That keeps token checks off the database, which may be the thing that's down.
	private val tokenLock = Mutex()
	private var tokenLoaded = false
	private var storedToken: HealthToken? = null

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
		saveToken(HealthToken(sha256Hex(token), clock.now().toEpochMilliseconds()))
		return token
	}

	suspend fun revokeToken() = saveToken(null)

	suspend fun tokenCreatedAt(): Instant? =
		loadToken()?.let { Instant.fromEpochMilliseconds(it.createdAtMillis) }

	/** Throws if the token has never been read and the database can't be reached. */
	suspend fun verifyToken(given: String): Boolean {
		val stored = loadToken() ?: return false
		return MessageDigest.isEqual(
			sha256Hex(given).toByteArray(Charsets.UTF_8),
			stored.hash.toByteArray(Charsets.UTF_8),
		)
	}

	private suspend fun loadToken(): HealthToken? = tokenLock.withLock {
		if (!tokenLoaded) {
			storedToken = configRepository.get(AdminServerConfig.HEALTH_TOKEN)
			tokenLoaded = true
		}
		storedToken
	}

	private suspend fun saveToken(token: HealthToken?) = tokenLock.withLock {
		configRepository.set(AdminServerConfig.HEALTH_TOKEN, token)
		storedToken = token
		tokenLoaded = true
	}

	companion object {
		private const val TOKEN_BYTES = 32
		private val CACHE_TTL = 5.seconds
		private val DB_TIMEOUT = 3.seconds

		/** How far past its scheduled run a job may be before it counts as overdue. */
		val OVERDUE_GRACE = 10.minutes

		/** How long a single tick may run before it is assumed hung. */
		val MAX_TICK_DURATION = 1.hours

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

		// lastRun is stamped when a tick starts and nextRun once it finishes, so a tick is in
		// flight while nextRun is unset (first tick) or not later than lastRun. A tick in
		// flight is allowed MAX_TICK_DURATION; an idle job must start within the grace period.
		private fun RecurringTaskStatus.isOverdue(now: Instant): Boolean {
			val started = lastRun ?: return false
			val due = nextRun
			return if (due == null || due <= started) {
				now > started + MAX_TICK_DURATION
			} else {
				now > due + OVERDUE_GRACE
			}
		}
	}
}
