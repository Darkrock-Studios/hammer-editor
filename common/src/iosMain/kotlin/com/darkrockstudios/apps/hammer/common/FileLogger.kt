package com.darkrockstudios.apps.hammer.common

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import okio.BufferedSink
import okio.FileHandle
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.time.Clock

/**
 * iOS file logger. Mirrors the Android [com.darkrockstudios.apps.hammer.android.FileLogger]:
 * it delegates console output to [DebugAntilog] and additionally appends each log line to a
 * per-run file under [getLogDirectory]. A single consumer coroutine owns the sink so writes
 * never race.
 */
class FileLogger(
	private val fileSystem: FileSystem = getPlatformFilesystem(),
	private val clock: Clock = Clock.System,
	private val scope: CoroutineScope,
) : Antilog() {
	private val debugAntilog = DebugAntilog()
	private val logsDir: Path = requireNotNull(getLogDirectory()) {
		"getLogDirectory() must be non-null on iOS"
	}.toPath()
	private val logFileName = getLogFilename()
	private val appendBuffer: BufferedSink
	private val messageChannel = Channel<String>(
		capacity = Channel.UNLIMITED,
		onUndeliveredElement = {
			if (getInDevelopmentMode()) {
				error("Undelivered log message! $it")
			}
		}
	)

	init {
		ensureLogsDirectory()
		appendBuffer = createLogFile().appendingSink().buffer()

		scope.launch {
			cullLogs()
			watchForLogs()
		}
	}

	// The consumer coroutine is the sole owner of appendBuffer: it writes,
	// flushes, and closes the sink so no other thread can race it.
	private suspend fun watchForLogs() {
		try {
			messageChannel.consumeEach { message ->
				appendBuffer.writeUtf8(message)
				appendBuffer.flush()
			}
		} finally {
			appendBuffer.close()
		}
	}

	override fun performLog(
		priority: LogLevel,
		tag: String?,
		throwable: Throwable?,
		message: String?
	) {
		// Delegate to DebugAntilog for console output
		debugAntilog.log(priority, tag, throwable, message)

		// Write to file
		if (message != null) {
			val timestamp = clock.now()
			val logLine = "$timestamp | $priority | ${tag ?: ""} | $message\n"
			messageChannel.trySendBlocking(logLine)
		}
	}

	fun close() {
		// Closing the channel ends the consumer, which then closes the sink.
		messageChannel.close()
	}

	private fun createLogFile(): FileHandle = fileSystem.openReadWrite(logFileName)

	private fun ensureLogsDirectory() {
		if (fileSystem.exists(logsDir).not()) {
			fileSystem.createDirectories(logsDir)
		}
	}

	private fun cullLogs() {
		val backups = getLogs().toMutableList()

		if (backups.size > MAX_LOGS) {
			val overBudget = backups.size - MAX_LOGS
			for (ii in 0 until overBudget) {
				val oldBackup = backups[ii]
				fileSystem.delete(oldBackup)
			}
		}
	}

	private fun getLogs(): List<Path> {
		return fileSystem.list(logsDir)
			.mapNotNull {
				try {
					val meta = fileSystem.metadata(it)
					Pair(it, meta)
				} catch (_: FileNotFoundException) {
					null
				}
			}
			.filter { it.second.isRegularFile }
			.sortedBy { it.second.lastModifiedAtMillis }
			.map { it.first }
	}

	private fun getLogFilename(): Path {
		val time = clock.now().toString().replace(":", "")
		return logsDir / "$time.txt"
	}

	companion object {
		private const val MAX_LOGS = 20
	}
}
