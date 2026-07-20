package com.darkrockstudios.apps.hammer.utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.streams.toList
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * A size-bounded, least-recently-used cache of byte blobs on disk. Entries are stored under
 * [directory], keyed by an arbitrary string (SHA-256-hashed to a safe filename). When a [put]
 * pushes the total size over [maxBytes], the least-recently-used entries are deleted until it
 * fits again.
 *
 * "Least-recently-used" is approximated by each file's last-modified time, which [get] refreshes
 * on a hit. Values are meant to be regenerable, so eviction — and losing the whole cache — is
 * harmless. [maxBytes] should comfortably exceed the largest single value; the newest entry is
 * never evicted, so a value larger than the cap simply stays as the sole occupant.
 *
 * Reads are lock-free; evictions are serialized. A read racing an eviction that deletes the same
 * file just yields a miss, which the caller regenerates. [getOrPut] is single-flight per key: a
 * burst of concurrent misses for the same key computes the value once rather than once per caller.
 *
 * The cache is an optimization and never a dependency: a full disk, a read-only directory, or any
 * other IO failure degrades to a miss, so callers keep serving computed values.
 */
class LruDiskCache(
	private val directory: Path,
	private val maxBytes: Long,
) {
	private val evictionLock = Any()
	// Striped locks bound the number of monitors while still serializing same-key computes
	// (same key -> same stripe). Distinct keys rarely collide; a collision only serializes.
	private val computeLocks = Array(COMPUTE_STRIPES) { Any() }
	private val computeMutexes = Array(COMPUTE_STRIPES) { Mutex() }

	// Pruning walks the whole directory, so amortize it: only sweep once writes since the last
	// sweep could plausibly have breached the cap. The bound is exceeded by at most this much.
	private val bytesSincePrune = AtomicLong(0)

	init {
		require(maxBytes > 0) { "maxBytes must be positive, was $maxBytes" }
		Files.createDirectories(directory)
		// Remove temp files left behind by an interrupted put.
		runCatching {
			listFiles { it.endsWith(TEMP_SUFFIX) }.forEach { runCatching { Files.deleteIfExists(it) } }
		}
	}

	fun get(key: String): ByteArray? {
		val file = fileFor(key)
		return try {
			if (!Files.exists(file)) return null
			val bytes = Files.readAllBytes(file)
			runCatching { Files.setLastModifiedTime(file, FileTime.from(Instant.now())) }
			bytes
		} catch (_: IOException) {
			null
		}
	}

	/**
	 * Store [value] under [key]. A write that fails leaves the cache without the entry rather than
	 * raising: the value is regenerable, so a caller must never fail because the disk did.
	 */
	fun put(key: String, value: ByteArray) {
		val target = fileFor(key)
		val temp = try {
			Files.createTempFile(directory, "put-", TEMP_SUFFIX)
		} catch (_: IOException) {
			return
		}
		try {
			Files.write(temp, value)
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
		} catch (_: IOException) {
			runCatching { Files.deleteIfExists(temp) }
			return
		}
		if (bytesSincePrune.addAndGet(value.size.toLong()) >= pruneThreshold) {
			bytesSincePrune.set(0)
			prune()
		}
	}

	fun getOrPut(key: String, compute: () -> ByteArray): ByteArray {
		get(key)?.let { return it }
		// Serialize computes for this key: a racer that computed while we waited wins the re-check.
		return synchronized(computeLockFor(key)) {
			get(key) ?: compute().also { put(key, it) }
		}
	}

	/**
	 * [getOrPut] for a suspending [compute], which cannot run inside a `synchronized` block. Same
	 * single-flight guarantee, held by a mutex rather than a monitor. Named apart from [getOrPut]
	 * because the two would be ambiguous at any lambda call site. The blocking file access moves to
	 * [Dispatchers.IO]; [compute] stays on the caller's context.
	 *
	 * A null from [compute] is returned to the caller without being stored, for values that turned
	 * out not to be worth keeping.
	 */
	suspend fun getOrPutSuspending(key: String, compute: suspend () -> ByteArray?): ByteArray? {
		withContext(Dispatchers.IO) { get(key) }?.let { return it }
		return computeMutexFor(key).withLock {
			withContext(Dispatchers.IO) { get(key) }
				?: compute()?.also { value -> withContext(Dispatchers.IO) { put(key, value) } }
		}
	}

	private fun computeLockFor(key: String): Any = computeLocks[stripeFor(key)]

	private fun computeMutexFor(key: String): Mutex = computeMutexes[stripeFor(key)]

	private fun stripeFor(key: String): Int = (key.hashCode() and 0x7fffffff) % COMPUTE_STRIPES

	/**
	 * Enforce the size bound now, evicting least-recently-used entries until the cache fits.
	 * [put] calls this, but a scheduled maintenance job can call it directly too.
	 */
	fun prune() {
		synchronized(evictionLock) { evictBySize() }
	}

	/** Delete entries not accessed within [maxAge], then enforce the size bound. */
	fun prune(maxAge: Duration) {
		synchronized(evictionLock) {
			val cutoff = Instant.now().minus(maxAge.toJavaDuration())
			for (file in listFiles { !it.endsWith(TEMP_SUFFIX) }) {
				val lastUsed = runCatching { Files.getLastModifiedTime(file).toInstant() }.getOrNull()
				if (lastUsed != null && lastUsed.isBefore(cutoff)) {
					runCatching { Files.deleteIfExists(file) }
				}
			}
			evictBySize()
		}
	}

	private fun evictBySize() {
		val entries = runCatching { listFiles { !it.endsWith(TEMP_SUFFIX) } }.getOrNull() ?: return
		var total = entries.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }
		if (total <= maxBytes) return

		var remaining = entries.size
		val oldestFirst = entries.sortedBy {
			runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L)
		}
		for (file in oldestFirst) {
			if (total <= maxBytes || remaining <= 1) break
			val size = runCatching { Files.size(file) }.getOrDefault(0L)
			if (runCatching { Files.deleteIfExists(file) }.getOrDefault(false)) {
				total -= size
				remaining--
			}
		}
	}

	private fun listFiles(predicate: (String) -> Boolean): List<Path> =
		Files.list(directory).use { stream ->
			stream.filter { Files.isRegularFile(it) && predicate(it.fileName.toString()) }.toList()
		}

	/** Exposed for tests to locate an entry's file (e.g. to assert or age it deterministically). */
	internal fun fileFor(key: String): Path = directory.resolve(hashKey(key))

	private fun hashKey(key: String): String = sha256Hex(key)

	// A tenth of the cap: frequent enough that the cache never drifts far past [maxBytes], rare
	// enough that a burst of writes doesn't walk the directory once per entry.
	private val pruneThreshold: Long = (maxBytes / PRUNE_THRESHOLD_DIVISOR).coerceAtLeast(1)

	private companion object {
		const val TEMP_SUFFIX = ".tmp"
		const val COMPUTE_STRIPES = 64
		const val PRUNE_THRESHOLD_DIVISOR = 10
	}
}
