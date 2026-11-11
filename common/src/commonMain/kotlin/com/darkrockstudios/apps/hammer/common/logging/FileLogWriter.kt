package com.darkrockstudios.apps.hammer.common.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import okio.BufferedSink
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import okio.buffer
import kotlin.time.Clock

/**
 * Appends each log line at [minSeverity] or above to a new file per run in [logsDir], keeping only
 * the newest [maxLogs] files there. A single consumer coroutine owns the sink, so writes never race.
 */
class FileLogWriter(
	private val logsDir: Path,
	private val maxLogs: Int,
	private val minSeverity: Severity = Severity.Verbose,
	private val fileSystem: FileSystem = getPlatformFilesystem(),
	private val clock: Clock = Clock.System,
	scope: CoroutineScope,
) : LogWriter() {
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
		fileSystem.createDirectories(logsDir)
		appendBuffer = fileSystem.openReadWrite(newLogFile()).appendingSink().buffer()

		scope.launch {
			cullLogs()
			watchForLogs()
		}
	}

	override fun isLoggable(tag: String, severity: Severity): Boolean = severity >= minSeverity

	override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
		// Append the stack trace so errors keep their throwable in the log.
		val body = if (throwable == null) message else "$message\n${throwable.stackTraceToString()}"
		// The channel is unlimited, so this only fails once the writer is closed.
		messageChannel.trySend("${clock.now()} | $severity | $tag | $body\n")
	}

	fun close() {
		// Closing the channel ends the consumer, which then closes the sink.
		messageChannel.close()
	}

	// The consumer coroutine is the sole owner of appendBuffer: it writes,
	// flushes, and closes the sink so no other thread can race it.
	private suspend fun watchForLogs() {
		try {
			messageChannel.consumeEach { line ->
				appendBuffer.writeUtf8(line)
				appendBuffer.flush()
			}
		} finally {
			appendBuffer.close()
		}
	}

	private fun cullLogs() {
		val logs = getLogs()
		if (logs.size > maxLogs) {
			logs.take(logs.size - maxLogs).forEach(fileSystem::delete)
		}
	}

	/** Oldest first. */
	private fun getLogs(): List<Path> {
		return fileSystem.list(logsDir)
			.mapNotNull {
				try {
					Pair(it, fileSystem.metadata(it))
				} catch (_: FileNotFoundException) {
					null
				}
			}
			.filter { it.second.isRegularFile }
			.sortedBy { it.second.lastModifiedAtMillis }
			.map { it.first }
	}

	private fun newLogFile(): Path {
		val time = clock.now().toString().replace(":", "")
		return logsDir / "$time.txt"
	}
}
