package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.utilities.truncateToUtcDay
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory accumulator of distinct (project, day, visitor) keys for the
 * best-effort "unique readers" metric on published / shared stories.
 *
 * Readers are anonymous, so there is no account to dedup on. Instead each view is
 * reduced to a cookieless visitor hash: sha256(rotating daily salt + IP +
 * user-agent + project id). The salt is random bytes held only in memory and
 * rotated every UTC day — never persisted — so the hash can't be reversed to an IP
 * nor linked across days. The raw IP is consumed here and never stored.
 *
 * A view is only recorded once the reader has actually dwelt on the page for a few
 * seconds — the public page fires a beacon on a client-side timer (story-reader.js)
 * rather than counting on page load — so drive-by clicks (bounces) never reach
 * [record]. That's a best-effort filter, not a guarantee.
 *
 * Recording is a lock-light concurrent-set add and a cheap no-op when
 * [setCollecting] has been told the feature is off. The maintenance job
 * periodically [drainToKeys] and persists them.
 */
class StoryReaderCollector(
	private val clock: Clock,
	private val maxPendingKeys: Int = MAX_PENDING_KEYS,
) {
	@Volatile
	private var collecting: Boolean = true

	private val keys = ConcurrentHashMap.newKeySet<ReaderKey>()

	private val saltLock = Any()
	private val random = SecureRandom()
	private var saltEpochDay: Long = Long.MIN_VALUE
	private lateinit var salt: String

	/** Called by the maintenance job each tick to reflect the live config. */
	fun setCollecting(enabled: Boolean) {
		collecting = enabled
	}

	fun isCollecting(): Boolean = collecting

	fun record(projectId: Long, clientIp: String?, userAgent: String?) {
		if (!collecting) return
		// Backstop against a flood of distinct keys (e.g. someone hammering the read
		// beacon with varied user-agents) growing the set without bound between the
		// once-a-minute drains. Past the cap we simply shed new reads until the next
		// drain frees the set — a best-effort metric may undercount under abuse, but
		// it must not exhaust memory. The cap sits far above any legitimate
		// unique-reader volume in a single drain window.
		if (keys.size >= maxPendingKeys) return
		val now = clock.now()
		val epochDay = now.epochSeconds / SECONDS_PER_DAY
		val dailySalt = currentSalt(epochDay)
		val hash = visitorHash(dailySalt, clientIp, userAgent, projectId)
		keys.add(ReaderKey(projectId, now.truncateToUtcDay(), hash))
	}

	/** Atomically removes and returns the accumulated keys. */
	fun drainToKeys(): List<ReaderKey> {
		val out = ArrayList<ReaderKey>(keys.size)
		val iterator = keys.iterator()
		while (iterator.hasNext()) {
			out += iterator.next()
			iterator.remove()
		}
		return out
	}

	private fun currentSalt(epochDay: Long): String = synchronized(saltLock) {
		if (epochDay != saltEpochDay) {
			val bytes = ByteArray(SALT_BYTES)
			random.nextBytes(bytes)
			salt = bytes.toHex()
			saltEpochDay = epochDay
		}
		salt
	}

	private fun visitorHash(salt: String, clientIp: String?, userAgent: String?, projectId: Long): String {
		val digest = MessageDigest.getInstance("SHA-256")
		val input = "$salt|${clientIp.orEmpty()}|${userAgent.orEmpty()}|$projectId"
		return digest.digest(input.toByteArray(Charsets.UTF_8)).toHex()
	}

	private fun ByteArray.toHex(): String = buildString(size * 2) {
		for (byte in this@toHex) append("%02x".format(byte))
	}

	private companion object {
		const val SECONDS_PER_DAY = 86_400L
		const val SALT_BYTES = 32

		// Upper bound on distinct keys held between drains (~once a minute). Well
		// above any real per-minute unique-reader count; only an abusive flood of
		// varied keys reaches it, at which point excess reads are shed to cap memory.
		const val MAX_PENDING_KEYS = 100_000
	}
}

/** A distinct unit of readership: one visitor, one story, within one UTC day. */
data class ReaderKey(
	val projectId: Long,
	val dayBucket: Instant,
	val visitorHash: String,
)
