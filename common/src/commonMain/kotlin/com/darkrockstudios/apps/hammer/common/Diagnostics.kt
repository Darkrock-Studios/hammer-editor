package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.common.util.crashDumpMillis
import com.darkrockstudios.apps.hammer.common.util.isCrashDump
import com.darkrockstudios.apps.hammer.common.util.readLatestCrash
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.time.Instant

/** Enough of the tail to hold a stack trace and what led up to it, without becoming an attachment. */
private const val DIAGNOSTICS_LOG_LINES = 200

/** Ceiling on the tail read, so a runaway log can't be pulled into memory whole. */
private const val DIAGNOSTICS_TAIL_BYTES = 64L * 1024

/** A crash's cause sits at the top; a StackOverflowError can repeat one frame for thousands of lines below it. */
private const val DIAGNOSTICS_CRASH_LINES = 300

private const val NO_LOG_MARKER = "(no log file found)"

internal const val DIAGNOSTICS_LOG_HEADER = "--- Current log ---"

/**
 * What a support request needs pasted into it: the startup banner (version, channel, OS), the most
 * recent crash dump when there is one, then the tail of the session's log. Always produces something,
 * so a missing log costs the reporter the log rather than the whole report.
 */
suspend fun buildDiagnosticsReport(
	logDirectoryPath: String,
	fileSystem: FileSystem = getPlatformFilesystem(),
): String = withContext(platformIoDispatcher) {
	val logDir = logDirectoryPath.toPath()
	val crash = readLatestCrashSection(fileSystem, logDir)
	val tail = readCurrentLogTail(fileSystem, logDir)
	buildString {
		append(startupBanner())
		if (crash != null) append("\n\n").append(crash)
		append("\n\n").append(DIAGNOSTICS_LOG_HEADER).append('\n').append(tail ?: NO_LOG_MARKER)
	}
}

private fun readLatestCrashSection(fileSystem: FileSystem, logDir: Path): String? {
	return try {
		val crash = readLatestCrash(fileSystem, logDir) ?: return null
		val crashedAt = crashDumpMillis(crash.fileName)?.let { " (${Instant.fromEpochMilliseconds(it)})" }.orEmpty()
		val lines = crash.content.trim().lines()
		val omitted = lines.size - DIAGNOSTICS_CRASH_LINES
		buildString {
			append("--- Latest crash: ${crash.fileName}$crashedAt ---\n")
			append(lines.take(DIAGNOSTICS_CRASH_LINES).joinToString("\n"))
			if (omitted > 0) append("\n($omitted more lines)")
		}
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		Napier.e("Failed to read the latest crash dump", e)
		null
	}
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
		.filter { it.name.endsWith(".txt") && !it.isCrashDump() }
		.mapNotNull { path -> fileSystem.metadataOrNull(path)?.let { path to it } }
		.filter { it.second.isRegularFile }
		.maxByOrNull { it.second.lastModifiedAtMillis ?: 0L }
		?.first
}
