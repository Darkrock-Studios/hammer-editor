package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.hostOs
import kotlin.system.exitProcess

/**
 * Java fixes the encoding of file names from the locale as it starts, and nothing set later changes
 * it (`-Dsun.jnu.encoding` is ignored). Started without a UTF-8 locale, as MCP clients start stdio
 * servers and as systemd and cron start jobs, every path with a non-ASCII character is unreadable,
 * including the `꞉` that stands in for a colon in chapter folder names. So on Linux, Hammer runs
 * itself again under C.UTF-8 and passes its standard streams and exit code through.
 */
internal fun relaunchUnderUtf8LocaleIfNeeded() {
	if (!needsUtf8Relaunch(hostOs, System.getProperty("sun.jnu.encoding"), System.getenv(RELAUNCHED_ENV) != null)) return
	val info = ProcessHandle.current().info()
	val command = info.command().orElse(null) ?: return
	val arguments = info.arguments().orElse(emptyArray())

	val child = ProcessBuilder(listOf(command) + arguments)
		.inheritIO()
		.apply {
			environment()["LC_ALL"] = UTF8_LOCALE
			environment()[RELAUNCHED_ENV] = "1"
			// The jpackage launcher marks its process with this; a child that inherits it is taken for the
			// launcher's own JVM restart, and its arguments for java's, so `mcp` becomes a main class.
			environment().remove(JPACKAGE_LAUNCHER_ENV)
		}
		.start()
	// A client that stops this process expects Hammer to stop with it.
	Runtime.getRuntime().addShutdownHook(Thread { child.destroy() })
	exitProcess(child.waitFor())
}

/** [alreadyRelaunched] stops a loop where C.UTF-8 is not installed, so the relaunch is no better off. */
internal fun needsUtf8Relaunch(os: HostOs, fileNameEncoding: String?, alreadyRelaunched: Boolean): Boolean =
	os == HostOs.Linux &&
		!alreadyRelaunched &&
		fileNameEncoding != null &&
		!fileNameEncoding.equals("UTF-8", ignoreCase = true)

private const val UTF8_LOCALE = "C.UTF-8"
private const val RELAUNCHED_ENV = "HAMMER_UTF8_RELAUNCHED"
private const val JPACKAGE_LAUNCHER_ENV = "_JPACKAGE_LAUNCHER"
