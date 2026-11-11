package com.darkrockstudios.apps.hammer.common.dependencyinjection

import co.touchlab.kermit.Severity
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import co.touchlab.kermit.Logger as KermitLogger

/** Routes Koin's own logging into Kermit. */
class KermitKoinLogger : Logger() {
	override fun display(level: Level, msg: MESSAGE) {
		KermitLogger.log(level.toSeverity(), "Koin", null, msg)
	}
}

private fun Level.toSeverity() = when (this) {
	Level.DEBUG -> Severity.Debug
	Level.INFO -> Severity.Info
	Level.WARNING -> Severity.Warn
	Level.ERROR -> Severity.Error
	Level.NONE -> Severity.Verbose
}
