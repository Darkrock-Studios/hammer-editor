package com.darkrockstudios.apps.hammer.desktop.cli

import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import io.github.aakira.napier.Napier
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * An app window for a config directory. Every window holds a shared lock on `window.lock` for its
 * whole run, so CLI calls never run headless under any of them. The one holding `app.lock` serves the
 * socket, so later launches hand off to it. It also takes the [WriterLock], waiting in the background
 * for a CLI call already running to finish; forwarded calls wait until it holds that lock and the app
 * has [started], for up to [readyWait].
 *
 * On POSIX, closing any channel to a file drops every lock the process holds on it, so a window never
 * opens a lock file it holds a second time.
 */
class AppInstance(
	private val directory: File,
	private val registry: () -> OperationRegistry,
	private val onLaunch: (args: List<String>) -> Unit,
	private val handOffWait: Duration = HAND_OFF_WAIT,
	private val retry: Duration = RETRY,
	private val readyWait: Duration = READY_WAIT,
) : AutoCloseable {
	private val socket = Forwarding.socketPath(directory)
	private val ready = CompletableDeferred<Boolean>()
	private var holdsWriter = false
	private var hasStarted = false
	private var window: FileLock? = null
	private var appLock: FileLock? = null
	private var server: Forwarding.Server? = null
	private var writerLock: WriterLock? = null
	private var waiting: Job? = null
	private var closed = false

	/**
	 * Claims the directory for this window, or hands [args] to the window that has it and returns false,
	 * so this process can exit. A window still starting gets [handOffWait] to answer; past that this one
	 * opens anyway, without the app and writer locks, and claims them once the other closes.
	 */
	fun open(scope: CoroutineScope, args: List<String>): Boolean {
		val deadline = TimeSource.Monotonic.markNow() + handOffWait
		try {
			while (true) {
				lockApp(directory)?.let {
					markWindow()
					claim(scope, it)
					return true
				}
				when (Forwarding.handOff(socket, args)) {
					Forwarding.HandOff.Taken -> return false
					Forwarding.HandOff.Failed -> break
					Forwarding.HandOff.NotListening -> if (deadline.hasPassedNow()) break
				}
				Thread.sleep(retry.inWholeMilliseconds)
			}
		} catch (e: IOException) {
			Napier.w(e) { "Could not take the app lock; running without it" }
			markWindow()
			return true
		}
		Napier.w { "Hammer is already running but did not take this launch; opening a second window" }
		markWindow()
		inBackground(scope) {
			while (true) {
				lockApp(directory)?.let {
					claim(scope, it)
					return@inBackground
				}
				delay(retry)
			}
		}
		return true
	}

	/** Koin is up and data migrated, so forwarded calls can run. */
	fun started() {
		synchronized(this) {
			hasStarted = true
			if (holdsWriter) ready.complete(true)
		}
	}

	private fun markWindow() {
		val lock = try {
			lockWindow(directory)
		} catch (e: IOException) {
			Napier.w(e) { UNMARKED_MESSAGE }
			return
		}
		if (lock == null) {
			Napier.w { UNMARKED_MESSAGE }
			return
		}
		synchronized(this) {
			if (closed) lock.channel().close() else window = lock
		}
	}

	private fun claim(scope: CoroutineScope, lock: FileLock) {
		synchronized(this) {
			if (closed) {
				lock.channel().close()
				return
			}
			appLock = lock
			server = startServer(scope)
		}
		if (!takeWriterLock()) {
			Napier.i { "A CLI call is using Hammer's data; taking the writer lock when it finishes" }
			inBackground(scope) {
				while (!takeWriterLock()) delay(retry)
			}
		}
	}

	private fun startServer(scope: CoroutineScope): Forwarding.Server? {
		var server: Forwarding.Server? = null
		return try {
			Forwarding.Server(socket, registry, onLaunch, awaitReady = {
				withTimeoutOrNull(readyWait) { ready.await() } == true
			}).also { server = it }.also { it.start(scope) }
		} catch (e: IOException) {
			Napier.w(e) { "Could not open the CLI socket; CLI calls will refuse while Hammer runs" }
			server?.close()
			null
		} catch (e: UnsupportedOperationException) {
			Napier.w(e) { "This system has no Unix domain sockets; CLI calls will refuse while Hammer runs" }
			null
		}
	}

	private fun takeWriterLock(): Boolean {
		val result = try {
			WriterLock.acquire(directory, WriterLock.Holder.App)
		} catch (e: IOException) {
			// No CLI call can take it either, and the window lock already keeps them from running headless.
			Napier.w(e) { "Could not take the writer lock; serving CLI calls without it" }
			null
		}
		if (result is WriterLock.Result.Busy) return false
		synchronized(this) {
			val lock = (result as? WriterLock.Result.Acquired)?.lock
			if (closed) {
				lock?.close()
			} else {
				writerLock = lock
				holdsWriter = true
				if (hasStarted) ready.complete(true)
			}
		}
		return true
	}

	private fun inBackground(scope: CoroutineScope, block: suspend () -> Unit) {
		synchronized(this) {
			if (closed) return
			waiting = scope.launch(Dispatchers.IO) {
				try {
					block()
				} catch (e: IOException) {
					Napier.w(e) { "Could not take Hammer's locks; running without them" }
				}
			}
		}
	}

	/**
	 * Lets calls in flight finish, then releases the window lock last, so a CLI call never finds the
	 * writer lock free while this still runs.
	 */
	override fun close() {
		synchronized(this) {
			if (closed) return
			closed = true
			ready.complete(false)
			waiting?.cancel()
			server?.close()
			writerLock?.close()
			appLock?.channel()?.close()
			window?.channel()?.close()
		}
	}

	companion object {
		const val LOCK_FILE_NAME = "app.lock"
		const val WINDOW_LOCK_FILE_NAME = "window.lock"
		private val HAND_OFF_WAIT = 10.seconds
		private val RETRY = 250.milliseconds
		private val READY_WAIT = 30.seconds

		/** CLI calls hold the window lock only for a moment, to check for windows. */
		private val WINDOW_WAIT = 2.seconds
		private val LOCK_RETRY = 10.milliseconds
		private const val UNMARKED_MESSAGE = "Could not mark this window open; CLI calls may run headless under it"

		/** Whether an app window is open on [directory]. Never asked by a window. */
		fun isOpen(directory: File): Boolean {
			val window = lockFile(directory, WINDOW_LOCK_FILE_NAME) ?: return true
			window.channel().close()
			val app = lockApp(directory) ?: return true
			app.channel().close()
			return false
		}

		/** The app lock, or null when another holds it. */
		internal fun lockApp(directory: File): FileLock? = lockFile(directory, LOCK_FILE_NAME)

		/** The window lock, shared by every window, or null when a CLI call keeps checking for windows. */
		internal fun lockWindow(directory: File): FileLock? =
			lockFile(directory, WINDOW_LOCK_FILE_NAME, shared = true, wait = WINDOW_WAIT)

		/** A lock on the whole of [name], retried on the same channel for up to [wait]; null when held. */
		private fun lockFile(directory: File, name: String, shared: Boolean = false, wait: Duration = Duration.ZERO): FileLock? {
			directory.mkdirs()
			val channel = FileChannel.open(
				File(directory, name).toPath(),
				StandardOpenOption.CREATE,
				StandardOpenOption.READ,
				StandardOpenOption.WRITE,
			)
			val deadline = TimeSource.Monotonic.markNow() + wait
			try {
				while (true) {
					channel.tryLockOrNull(shared)?.let { return it }
					if (deadline.hasPassedNow()) break
					Thread.sleep(LOCK_RETRY.inWholeMilliseconds)
				}
			} catch (e: IOException) {
				channel.close()
				throw e
			}
			channel.close()
			return null
		}
	}
}
