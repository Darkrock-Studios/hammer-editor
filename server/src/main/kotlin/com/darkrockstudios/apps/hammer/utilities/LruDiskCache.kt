package com.darkrockstudios.apps.hammer.utilities

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Instant
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
 * file just yields a miss, which the caller regenerates.
 */
class LruDiskCache(
	private val directory: Path,
	private val maxBytes: Long,
) {
	private val evictionLock = Any()

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

	fun put(key: String, value: ByteArray) {
		val target = fileFor(key)
		val temp = Files.createTempFile(directory, "put-", TEMP_SUFFIX)
		try {
			Files.write(temp, value)
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
		} catch (e: IOException) {
			runCatching { Files.deleteIfExists(temp) }
			throw e
		}
		prune()
	}

	fun getOrPut(key: String, compute: () -> ByteArray): ByteArray =
		get(key) ?: compute().also { put(key, it) }

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

	private fun hashKey(key: String): String =
		MessageDigest.getInstance("SHA-256")
			.digest(key.toByteArray(Charsets.UTF_8))
			.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

	private companion object {
		const val TEMP_SUFFIX = ".tmp"
	}
}
