package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.aakira.napier.Napier
import okio.ByteString.Companion.toByteString
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.random.Random

/**
 * One plugin's key-value cache under [directory], one file per key. Best effort: a failure to read
 * is a miss and a failure to write is dropped. Once the values pass [maxBytes], the oldest written
 * are dropped. Another process, such as a CLI command, may write to it at the same time.
 */
class PluginCache(
	private val fileSystem: FileSystem,
	private val directory: Path,
	private val maxBytes: Long = MAX_BYTES,
) {
	// Bytes held as of the last count, kept up to date with this process's writes only.
	private var held: Long? = null

	fun get(key: ByteArray): ByteArray? {
		checkKey(key)
		return try {
			fileSystem.read(pathOf(key)) { readByteArray() }
		} catch (_: FileNotFoundException) {
			null
		} catch (e: IOException) {
			Napier.w(e) { "Plugin cache read failed" }
			null
		}
	}

	/** Stores [value] under [key], or removes the key when [value] is null. */
	fun set(key: ByteArray, value: ByteArray?) {
		checkKey(key)
		require(value == null || value.size <= MAX_VALUE_BYTES) { "Cache value is over $MAX_VALUE_BYTES bytes" }
		val path = pathOf(key)
		try {
			val total = held ?: count()
			val replaced = fileSystem.metadataOrNull(path)?.size ?: 0
			if (value == null) {
				fileSystem.delete(path, mustExist = false)
				held = total - replaced
				return
			}
			fileSystem.createDirectories(directory)
			val staging = directory / "${path.name}.${Random.nextLong().toULong()}$STAGING_SUFFIX"
			fileSystem.write(staging) { write(value) }
			fileSystem.atomicMove(staging, path)
			held = (total - replaced + value.size).let { if (it > maxBytes) evict() else it }
		} catch (e: IOException) {
			Napier.w(e) { "Plugin cache write failed" }
			held = null
		}
	}

	fun clear() {
		try {
			fileSystem.deleteRecursively(directory)
		} catch (e: IOException) {
			Napier.w(e) { "Plugin cache could not be cleared" }
		}
		held = null
	}

	private fun checkKey(key: ByteArray) =
		require(key.size <= MAX_KEY_BYTES) { "Cache key is over $MAX_KEY_BYTES bytes" }

	// Hashed, so any bytes make a safe file name.
	private fun pathOf(key: ByteArray): Path = directory / key.toByteString().sha256().hex()

	// Staging files count too, so one a failed write left behind is dropped in time.
	private fun entries(): List<Pair<Path, FileMetadata>> =
		(fileSystem.listOrNull(directory) ?: emptyList())
			.mapNotNull { path -> fileSystem.metadataOrNull(path)?.let { path to it } }

	private fun count(): Long = entries().sumOf { it.second.size ?: 0 }

	// Drops the oldest written down to three quarters of the limit, so the next few writes need not.
	private fun evict(): Long {
		var total = count()
		for ((path, metadata) in entries().sortedBy { it.second.lastModifiedAtMillis ?: 0 }) {
			if (total <= maxBytes * 3 / 4) break
			fileSystem.delete(path, mustExist = false)
			total -= metadata.size ?: 0
		}
		return total
	}

	companion object {
		const val MAX_BYTES = 32L * 1024 * 1024
		const val MAX_KEY_BYTES = 1024
		const val MAX_VALUE_BYTES = 1024 * 1024
		private const val STAGING_SUFFIX = ".partial"
	}
}
