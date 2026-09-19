package com.darkrockstudios.apps.hammer.common

import io.github.aakira.napier.Napier
import java.io.File

/**
 * Catch, log, and hand off any otherwise-unhandled exception. The async [FileLogger] can't be
 * relied on to flush before the process dies, so we also write a synchronous crash dump straight
 * to disk, then chain to the platform default handler so Android still terminates and reports.
 */
fun installGlobalExceptionHandler() {
	val previous = Thread.getDefaultUncaughtExceptionHandler()
	Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
		runCatching {
			Napier.e(
				"Uncaught exception on thread '${thread.name}', terminating",
				throwable
			)
		}
		runCatching { writeCrashDump(thread, throwable) }
		previous?.uncaughtException(thread, throwable)
	}
}

/** Synchronous, self-contained crash record in the logs dir: the guaranteed artifact when the app dies. */
private fun writeCrashDump(thread: Thread, throwable: Throwable) {
	val dir = File(getConfigDirectory(), "logs")
	dir.mkdirs()
	File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(
		buildString {
			append(startupBanner() + "\n")
			append("Uncaught exception on thread '${thread.name}'\n\n")
			append(throwable.stackTraceToString())
		}
	)
}
