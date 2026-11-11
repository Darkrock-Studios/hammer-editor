package com.darkrockstudios.apps.hammer.common

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import com.darkrockstudios.apps.hammer.common.logging.FileLogWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException
import kotlin.time.Clock

// This file keeps its Napier-era name: Swift calls NapierProxyKt.debugBuild().

private const val MAX_LOGS = 20

fun debugBuild() {
	// Log to the console and persist each run's log to disk. The scope lives for the
	// lifetime of the app, matching Android's applicationScope.
	val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	val logsDir = requireNotNull(getLogDirectory()) { "getLogDirectory() must be non-null on iOS" }.toPath()
	Logger.setLogWriters(
		platformLogWriter(),
		FileLogWriter(logsDir = logsDir, maxLogs = MAX_LOGS, scope = scope),
	)
	installGlobalExceptionHandler()
}

/**
 * Catch, log, and write a crash dump for any otherwise-unhandled Kotlin exception before the app
 * dies, then hand back to the runtime so the OS still records its own crash report. Mirrors the
 * desktop/Android handlers. Note the narrower reach: only Kotlin exceptions (including those
 * crossing the Kotlin→Obj-C interop boundary) reach this hook — pure Obj-C NSExceptions and native
 * signals (e.g. SIGSEGV) are not caught here.
 */
@OptIn(ExperimentalNativeApi::class)
private fun installGlobalExceptionHandler() {
	setUnhandledExceptionHook { throwable ->
		runCatching { Logger.e("Uncaught exception, terminating", throwable) }
		runCatching { writeCrashDump(throwable) }
		// Preserve the standard termination so a native crash report is still produced.
		terminateWithUnhandledException(throwable)
	}
}

/** Synchronous, self-contained crash record in the logs dir — the guaranteed artifact when the app dies. */
private fun writeCrashDump(throwable: Throwable) {
	val dir = getLogDirectory()?.toPath() ?: return
	val fileSystem = getPlatformFilesystem()
	fileSystem.createDirectories(dir)
	val file = dir / "crash-${Clock.System.now().toEpochMilliseconds()}.txt"
	fileSystem.write(file) {
		writeUtf8(startupBanner() + "\n")
		writeUtf8("Uncaught exception\n\n")
		writeUtf8(throwable.stackTraceToString())
	}
}
