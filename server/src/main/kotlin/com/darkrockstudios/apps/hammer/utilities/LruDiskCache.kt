package com.darkrockstudios.apps.hammer.utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * A size-bounded, least-recently-used cache of byte blobs on disk. Entries are stored under
 * [directory], keyed by an arbitrary string (SHA-256-hashed to a safe filename). When a [put]
 * pushes the total size over [maxBytes], the least-recently-used entries are deleted until it
 * fits again.
 *
 * "Least-recently-used" is approximated by each file's last-modified time. okio offers no way to
 * set that directly, so [get] refreshes it by rewriting the entry — throttled to [TOUCH_INTERVAL],
 * since the only thing recency has to survive is the size bound and an age-based sweep measured in
 * days. A frequently-read entry therefore rewrites a handful of times a day, not once per read.
 *
 * Values are meant to be regenerable, so eviction — and losing the whole cache — is harmless.
 * [maxBytes] should comfortably exceed the largest single value; the newest entry is never evicted,
 * so a value larger than the cap simply stays as the sole occupant.
 *
 * Reads are lock-free; evictions are serialized. A read racing an eviction that deletes the same
 * file just yields a miss, which the caller regenerates. [getOrPut] is single-flight per key: a
 * burst of concurrent misses for the same key computes the value once rather than once per caller.
 *
 * The cache is an optimization and never a dependency: a full disk, a read-only directory, or any
 * other IO failure degrades to a miss, so callers keep serving computed values.
 */
class LruDiskCache(
	private val fileSystem: FileSystem,
	private val directory: Path,
	private val maxBytes: Long,
	private val clock: Clock = Clock.System,
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
		fileSystem.createDirectories(directory)
		// Remove temp files left behind by an interrupted put.
		runCatching {
			listFiles { it.endsWith(TEMP_SUFFIX) }.forEach { delete(it) }
		}
	}

	fun get(key: String): ByteArray? {
		val file = fileFor(key)
		return try {
			val bytes = fileSystem.read(file) { readByteArray() }
			touch(file, bytes)
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
		if (!write(fileFor(key), value)) return

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
			val cutoff = clock.now() - maxAge
			for (file in listFiles { !it.endsWith(TEMP_SUFFIX) }) {
				val lastUsed = lastModified(file) ?: continue
				if (lastUsed < cutoff) delete(file)
			}
			evictBySize()
		}
	}

	/**
	 * Refresh the entry's recency. okio can't set a modification time, so this rewrites the file,
	 * which is only worth doing once the recorded time has actually gone stale.
	 */
	private fun touch(file: Path, bytes: ByteArray) {
		val lastUsed = lastModified(file) ?: return
		if (clock.now() - lastUsed < TOUCH_INTERVAL) return
		write(file, bytes)
	}

	/** Atomically replace [file] with [value]. False when the write failed and was rolled back. */
	private fun write(file: Path, value: ByteArray): Boolean {
		val temp = directory / "put-${UUID.randomUUID()}$TEMP_SUFFIX"
		return try {
			fileSystem.write(temp) { write(value) }
			fileSystem.atomicMove(temp, file)
			true
		} catch (_: IOException) {
			delete(temp)
			false
		}
	}

	private fun evictBySize() {
		val entries = runCatching { listFiles { !it.endsWith(TEMP_SUFFIX) } }.getOrNull() ?: return
		var total = entries.sumOf { size(it) }
		if (total <= maxBytes) return

		var remaining = entries.size
		val oldestFirst = entries.sortedBy { lastModified(it)?.toEpochMilliseconds() ?: 0L }
		for (file in oldestFirst) {
			if (total <= maxBytes || remaining <= 1) break
			val size = size(file)
			if (delete(file)) {
				total -= size
				remaining--
			}
		}
	}

	private fun listFiles(predicate: (String) -> Boolean): List<Path> =
		fileSystem.listOrNull(directory)
			.orEmpty()
			.filter { fileSystem.metadataOrNull(it)?.isRegularFile == true && predicate(it.name) }

	private fun size(file: Path): Long = fileSystem.metadataOrNull(file)?.size ?: 0L

	private fun lastModified(file: Path) =
		fileSystem.metadataOrNull(file)?.lastModifiedAtMillis?.let { kotlin.time.Instant.fromEpochMilliseconds(it) }

	private fun delete(file: Path): Boolean =
		runCatching { fileSystem.delete(file, mustExist = false) }.isSuccess

	private fun computeLockFor(key: String): Any = computeLocks[stripeFor(key)]

	private fun computeMutexFor(key: String): Mutex = computeMutexes[stripeFor(key)]

	private fun stripeFor(key: String): Int = (key.hashCode() and 0x7fffffff) % COMPUTE_STRIPES

	/** Exposed for tests to locate an entry's file (e.g. to assert or age it deterministically). */
	internal fun fileFor(key: String): Path = directory / sha256Hex(key)

	// A tenth of the cap: frequent enough that the cache never drifts far past [maxBytes], rare
	// enough that a burst of writes doesn't walk the directory once per entry.
	private val pruneThreshold: Long = (maxBytes / PRUNE_THRESHOLD_DIVISOR).coerceAtLeast(1)

	private companion object {
		const val TEMP_SUFFIX = ".tmp"
		const val COMPUTE_STRIPES = 64
		const val PRUNE_THRESHOLD_DIVISOR = 10
		val TOUCH_INTERVAL = 6.hours
	}
}
