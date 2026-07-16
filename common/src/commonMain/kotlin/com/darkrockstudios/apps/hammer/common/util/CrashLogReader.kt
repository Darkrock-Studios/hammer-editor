package com.darkrockstudios.apps.hammer.common.util

import okio.FileSystem
import okio.IOException
import okio.Path

/** A crash dump found in the logs directory. */
data class CrashReport(
	val fileName: String,
	val content: String,
)

/**
 * Reads the most recent `crash-*.txt` dump from [logDir], or null when there are none
 * (or the directory is unreadable).
 *
 * Crash files are named `crash-<epochMillis>.txt`; the millis come straight from
 * `System.currentTimeMillis()` at crash time, so ordering by the parsed timestamp is
 * reliable and portable. Files whose name doesn't parse fall back to their filesystem
 * modified time.
 */
fun readLatestCrash(fileSystem: FileSystem, logDir: Path): CrashReport? {
	val newest = try {
		if (!fileSystem.exists(logDir)) return null
		fileSystem.list(logDir)
			.filter { it.name.startsWith(CRASH_PREFIX) && it.name.endsWith(CRASH_SUFFIX) }
			.mapNotNull { path ->
				try {
					if (fileSystem.metadata(path).isRegularFile) path to crashSortKey(fileSystem, path) else null
				} catch (_: IOException) {
					null
				}
			}
			.maxByOrNull { it.second }
			?.first
	} catch (_: IOException) {
		null
	} ?: return null

	val content = try {
		fileSystem.read(newest) { readUtf8() }
	} catch (_: IOException) {
		return null
	}

	return CrashReport(fileName = newest.name, content = content)
}

private fun crashSortKey(fileSystem: FileSystem, path: Path): Long {
	val fromName = path.name
		.removePrefix(CRASH_PREFIX)
		.removeSuffix(CRASH_SUFFIX)
		.toLongOrNull()
	if (fromName != null) return fromName

	return try {
		fileSystem.metadata(path).lastModifiedAtMillis ?: 0L
	} catch (_: IOException) {
		0L
	}
}

private const val CRASH_PREFIX = "crash-"
private const val CRASH_SUFFIX = ".txt"
