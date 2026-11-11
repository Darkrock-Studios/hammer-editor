package com.darkrockstudios.apps.hammer.desktop

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/** Console logging on stderr, never stdout, which carries a CLI call's JSON. */
class StderrLogWriter(private val minSeverity: Severity) : LogWriter() {
	override fun isLoggable(tag: String, severity: Severity): Boolean = severity >= minSeverity

	override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
		val line = "[$severity] $tag - $message"
		System.err.println(if (throwable == null) line else "$line\n${throwable.stackTraceToString()}")
	}
}
