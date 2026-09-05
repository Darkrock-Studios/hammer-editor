package com.darkrockstudios.apps.hammer.common

import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/** Enough of the tail to hold a stack trace and what led up to it, without becoming an attachment. */
private const val DIAGNOSTICS_LOG_LINES = 200

/** Ceiling on the tail read, so a runaway log can't be pulled into memory whole. */
private const val DIAGNOSTICS_TAIL_BYTES = 64L * 1024

private const val NO_LOG_MARKER = "(no log file found)"

/**
 * What a support request needs pasted into it: the startup banner (version, channel, OS) followed
 * by the tail of the session's log. Always produces something, so a missing log costs the reporter
 * the log rather than the whole report.
 */
suspend fun buildDiagnosticsReport(
	logDirectoryPath: String,
	fileSystem: FileSystem = getPlatformFilesystem(),
): String = withContext(platformIoDispatcher) {
	val tail = readCurrentLogTail(fileSystem, logDirectoryPath.toPath())
	"${startupBanner()}\n\n${tail ?: NO_LOG_MARKER}"
}

private fun readCurrentLogTail(fileSystem: FileSystem, logDir: Path): String? {
	return try {
		val logFile = currentLogFile(fileSystem, logDir) ?: return null
		fileSystem.openReadOnly(logFile).use { handle ->
			val from = (handle.size() - DIAGNOSTICS_TAIL_BYTES).coerceAtLeast(0L)
			val buffer = Buffer()
			handle.source(from).buffer().use { it.readAll(buffer) }

			val lines = buffer.readUtf8().lineSequence()
				// Seeking by byte offset lands mid-line; drop the fragment rather than ship a torn line.
				.let { if (from > 0L) it.drop(1) else it }
				.toList()
			lines.takeLast(DIAGNOSTICS_LOG_LINES).joinToString("\n").trim().ifEmpty { null }
		}
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		Napier.e("Failed to read the current log file", e)
		null
	}
}

private fun currentLogFile(fileSystem: FileSystem, logDir: Path): Path? {
	if (!fileSystem.exists(logDir)) return null
	return fileSystem.list(logDir)
		.filter { it.name.endsWith(".txt") }
		.mapNotNull { path -> fileSystem.metadataOrNull(path)?.let { path to it } }
		.filter { it.second.isRegularFile }
		.maxByOrNull { it.second.lastModifiedAtMillis ?: 0L }
		?.first
}
