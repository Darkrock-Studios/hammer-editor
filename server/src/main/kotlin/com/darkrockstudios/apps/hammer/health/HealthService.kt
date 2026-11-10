package com.darkrockstudios.apps.hammer.health

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.HealthCheckDao
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskStatus
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.SharedTimedCall
import com.darkrockstudios.apps.hammer.utilities.sha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
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
	ioDispatcher: CoroutineContext,
	private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
	private val logger = LoggerFactory.getLogger(HealthService::class.java)
	private val tokenGenerator = SecureTokenGenerator(TOKEN_BYTES, base64)

	// One computation serves every probe inside the cache window, so several
	// monitors polling together cost a single database ping.
	private val reportLock = Mutex()
	private var cached: HealthReport? = null
	private var cachedAt: ComparableTimeMark? = null

	// This service is the only writer of the token, so after the first read it is served
	// from memory without locking. That keeps token checks off the database, which may be the
	// thing that's down. The first read is shared and bounded, so while the database hangs
	// every probe gives up after TOKEN_READ_TIMEOUT instead of queueing behind one read.
	private val knownToken = AtomicReference<KnownToken?>(null)
	private val tokenRead = SharedTimedCall(ioDispatcher) { configRepository.get(AdminServerConfig.HEALTH_TOKEN) }
	private val tokenWriteLock = Mutex()

	/** Distinguishes "no token issued" (token = null) from "not read yet" (no KnownToken). */
	private class KnownToken(val token: HealthToken?)

	private val dbFailureLog = LogThrottle(LOG_INTERVAL)

	suspend fun report(): HealthReport = reportLock.withLock {
		val last = cached
		val lastAt = cachedAt
		if (last != null && lastAt != null && lastAt.elapsedNow() < CACHE_TTL) return@withLock last

		buildReport(clock.now()).also {
			cached = it
			cachedAt = timeSource.markNow()
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
				if (dbFailureLog.shouldLog("ping-timeout")) logger.warn("Health check database ping timed out after {}", DB_TIMEOUT)
				DatabaseHealth(DatabaseState.TIMEOUT, null)
			}
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			if (dbFailureLog.shouldLog("ping")) logger.warn("Health check database ping failed", e)
			DatabaseHealth(DatabaseState.DOWN, null)
		}
	}

	suspend fun generateToken(): String {
		val token = tokenGenerator.generateToken()
		saveToken(HealthToken(sha256Hex(token), clock.now().toEpochMilliseconds()))
		return token
	}

	suspend fun revokeToken() = saveToken(null)

	/** Waits longer than a probe would: this is for the admin page, not a monitor. */
	suspend fun tokenCreatedAt(): Instant? =
		loadToken(ADMIN_TOKEN_READ_TIMEOUT)?.let { Instant.fromEpochMilliseconds(it.createdAtMillis) }

	/** Throws [TokenUnavailableException] or the read's own error if the token can't be read. */
	suspend fun verifyToken(given: String): Boolean {
		val stored = loadToken(TOKEN_READ_TIMEOUT) ?: return false
		return MessageDigest.isEqual(
			sha256Hex(given).toByteArray(Charsets.UTF_8),
			stored.hash.toByteArray(Charsets.UTF_8),
		)
	}

	private suspend fun loadToken(timeout: Duration): HealthToken? {
		knownToken.get()?.let { return it.token }
		val read = try {
			tokenRead.get(timeout)
		} catch (e: SharedTimedCall.UnavailableException) {
			throw TokenUnavailableException(e)
		}
		// Only the first answer is cached: a token saved while this read was in flight is newer.
		knownToken.compareAndSet(null, KnownToken(read))
		return knownToken.get()!!.token
	}

	// Probes never wait on tokenWriteLock, so a hung write can't stall token checks; it only
	// orders concurrent admin saves.
	private suspend fun saveToken(token: HealthToken?) = tokenWriteLock.withLock {
		configRepository.set(AdminServerConfig.HEALTH_TOKEN, token)
		knownToken.set(KnownToken(token))
	}

	/** Abandons a token read still in flight; called when the DI container shuts down. */
	fun close() {
		tokenRead.close()
	}

	class TokenUnavailableException(cause: Throwable) : Exception("Could not read the health token in time", cause)

	companion object {
		private const val TOKEN_BYTES = 32
		private val CACHE_TTL = 5.seconds
		private val DB_TIMEOUT = 3.seconds
		private val TOKEN_READ_TIMEOUT = 3.seconds
		private val ADMIN_TOKEN_READ_TIMEOUT = 30.seconds
		private val LOG_INTERVAL = 1.minutes

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

		/**
		 * Keys each status by its slug. Names that slug alike get a suffix derived from their
		 * full name, lengthened until no two jobs share a slug. A contested slug goes to none of
		 * its claimants, so the result depends only on the set of names, never on their order.
		 */
		fun jobsBySlug(statuses: List<RecurringTaskStatus>): Map<String, RecurringTaskStatus> {
			val bases = statuses.associateWith { slugOf(it.name).ifEmpty { "job" } }
			val shared = bases.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
			var slugs = bases.mapValues { (status, base) ->
				if (base in shared) suffixed(base, status, SLUG_SUFFIX_LENGTH) else base
			}
			var length = SLUG_SUFFIX_LENGTH
			while (length < SHA256_HEX_LENGTH) {
				val contested = slugs.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
				if (contested.isEmpty()) break
				length += SLUG_SUFFIX_LENGTH
				slugs = slugs.mapValues { (status, slug) ->
					if (slug in contested) suffixed(bases.getValue(status), status, length) else slug
				}
			}
			// Only contrived names survive a full-length hash; number them by name to stay unique.
			return slugs.entries
				.groupBy({ it.value }, { it.key })
				.flatMap { (slug, group) ->
					if (group.size == 1) listOf(slug to group.single())
					else group.sortedBy { it.name }.mapIndexed { i, status -> "$slug-${i + 1}" to status }
				}
				.toMap()
		}

		private fun suffixed(base: String, status: RecurringTaskStatus, length: Int) =
			"$base-${sha256Hex(status.name).take(length)}"

		private const val SLUG_SUFFIX_LENGTH = 6
		private const val SHA256_HEX_LENGTH = 64

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
