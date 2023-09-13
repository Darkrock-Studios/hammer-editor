package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import okio.*
import okio.Path.Companion.toPath
import java.util.logging.LogRecord
import java.util.logging.StreamHandler

class FileLogger(
	private val fileSystem: FileSystem = getPlatformFilesystem(),
	private val clock: Clock = Clock.System,
	private val scope: CoroutineScope,
) : StreamHandler() {
	private val getParentDir: Path = getConfigDirectory().toPath()
	private val logsDir = getLogsDirectory()
	private val logFileName = getLogFilename()
	private val appendBuffer: BufferedSink
	private val messageChannel = Channel<LogRecord>(
		capacity = Channel.UNLIMITED,
		onUndeliveredElement = {
			// I don't think this should happen, so let's throw if it does, so we know
			if (getInDevelopmentMode()) {
				error("Undelivered log message! ${it.message}")
			}
		}
	)

	init {
		appendBuffer = createLogFile().appendingSink().buffer()

		scope.launch {
			cullLogs()
			watchForLogs()
		}
	}

	private suspend fun watchForLogs() {
		messageChannel.consumeEach { record ->
			writeLogMessage(record)
			flush()
		}
	}

	override fun publish(record: LogRecord?) {
		super.publish(record)

		if (record != null) {
			messageChannel.trySendBlocking(record)
		}
	}

	private fun writeLogMessage(record: LogRecord) {
		appendBuffer.writeUtf8(
			"${record.instant} | ${record.message}\n"
		)
	}

	override fun close() {
		super.close()
		appendBuffer.close()
	}

	override fun flush() {
		super.flush()
		appendBuffer.flush()
	}

	private fun createLogFile(): FileHandle = fileSystem.openReadWrite(logFileName)

	private fun getLogsDirectory(): Path {
		val dir = getParentDir / LOG_DIRECTORY

		if (fileSystem.exists(dir).not()) {
			fileSystem.createDirectories(dir)
		}

		return dir
	}

	private fun cullLogs() {
		val backups = getLogs().toMutableList()

		// Delete the oldest logs to get under budget
		if (backups.size > MAX_LOGS) {
			val overBudget = backups.size - MAX_LOGS
			Napier.i("Logs over budget by $overBudget logs.")
			for (ii in 0 until overBudget) {
				val oldBackup = backups[ii]
				fileSystem.delete(oldBackup)
				Napier.i("Deleted log: $oldBackup")
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
		private const val LOG_DIRECTORY = "logs"
		private const val MAX_LOGS = 50
	}
}