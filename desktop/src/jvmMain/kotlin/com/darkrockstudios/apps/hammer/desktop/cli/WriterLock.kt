package com.darkrockstudios.apps.hammer.desktop.cli

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The one writer of Hammer's data at a time: the app for its whole run, or a single CLI call. An OS
 * file lock, so it is released when the holding process dies and never goes stale. The holder also
 * records its [Holder] beside the lock, so a waiter knows whether waiting can help.
 */
class WriterLock private constructor(
	private val channel: FileChannel,
	private val lock: FileLock,
	private val ownerFile: File,
) : AutoCloseable {

	enum class Holder { App, Cli }

	override fun close() {
		ownerFile.delete()
		lock.release()
		channel.close()
	}

	companion object {
		const val FILE_NAME = "writer.lock"
		private const val OWNER_FILE_NAME = "writer.owner"
		private val RETRY = 100.milliseconds

		/**
		 * Takes the lock in [directory] as [holder], retrying for up to [wait] while another CLI call
		 * holds it; the app holds it for its whole run, so waiting on the app is pointless.
		 */
		fun acquire(directory: File, holder: Holder, wait: Duration = Duration.ZERO): Result {
			directory.mkdirs()
			val ownerFile = File(directory, OWNER_FILE_NAME)
			val channel = FileChannel.open(
				File(directory, FILE_NAME).toPath(),
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
			)
			val deadline = TimeSource.Monotonic.markNow() + wait
			while (true) {
				val lock = try {
					channel.tryLock()
				} catch (e: OverlappingFileLockException) {
					null
				} catch (e: IOException) {
					channel.close()
					throw e
				}
				if (lock != null) {
					ownerFile.writeText(holder.name)
					return Result.Acquired(WriterLock(channel, lock, ownerFile))
				}
				val current = currentHolder(ownerFile)
				if (current == Holder.App || deadline.hasPassedNow()) {
					channel.close()
					return Result.Busy(current)
				}
				Thread.sleep(RETRY.inWholeMilliseconds)
			}
		}

		private fun currentHolder(ownerFile: File): Holder? = try {
			Holder.entries.firstOrNull { it.name == ownerFile.readText().trim() }
		} catch (e: IOException) {
			null
		}
	}

	sealed interface Result {
		class Acquired(val lock: WriterLock) : Result
		class Busy(val holder: Holder?) : Result
	}
}
