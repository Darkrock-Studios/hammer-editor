package com.darkrockstudios.apps.hammer.common.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * Tags a line with the simple name of the class that logged it, when the call gave no tag,
 * as Napier's DebugAntilog did on the JVM.
 */
class CallerTagWriter(private val delegate: LogWriter) : LogWriter() {
	override fun isLoggable(tag: String, severity: Severity): Boolean = delegate.isLoggable(tag, severity)

	override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
		delegate.log(severity, message, tag.ifEmpty { callerTag() }, throwable)
	}
}

private val LOGGING_PACKAGE = CallerTagWriter::class.java.packageName + "."

/** The first class on the stack outside Kermit and this package. Kermit's log calls are inline, so that is the caller. */
private fun callerTag(): String {
	val caller = Throwable().stackTrace.firstOrNull { frame ->
		!frame.className.startsWith("co.touchlab.kermit.") && !frame.className.startsWith(LOGGING_PACKAGE)
	} ?: return ""
	return caller.className.substringAfterLast('.').substringBefore('$')
}
