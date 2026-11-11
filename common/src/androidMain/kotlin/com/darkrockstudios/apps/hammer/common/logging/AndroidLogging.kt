package com.darkrockstudios.apps.hammer.common.logging

import co.touchlab.kermit.LogcatWriter
import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

private const val MAX_LOGS = 20

/**
 * Sends logs to logcat and to a per-run file in the config directory's "logs", tagged by the class
 * that logged. Call from Application.onCreate once the config directory is set.
 */
fun installAndroidLogging(scope: CoroutineScope) {
	val logsDir = getConfigDirectory().toPath() / "logs"
	Logger.setLogWriters(
		CallerTagWriter(LogcatWriter()),
		CallerTagWriter(FileLogWriter(logsDir = logsDir, maxLogs = MAX_LOGS, scope = scope)),
	)
}
