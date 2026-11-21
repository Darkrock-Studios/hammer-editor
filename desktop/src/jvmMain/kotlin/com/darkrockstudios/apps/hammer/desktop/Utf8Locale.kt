package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.hostOs
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Java fixes the encoding of file names from the locale as it starts, and nothing set later changes
 * it (`-Dsun.jnu.encoding` is ignored). Started without a UTF-8 locale, as MCP clients start stdio
 * servers and as systemd and cron start jobs, every path with a non-ASCII character is unreadable,
 * including the `꞉` that stands in for a colon in chapter folder names. So on Linux, Hammer replaces
 * itself with a fresh start under a UTF-8 `LC_CTYPE`, through `execv`.
 *
 * Replacing the process, rather than running a child, keeps the PID, standard streams, signals and
 * exit code Hammer's own. The arguments are the raw bytes of `/proc/self/cmdline`: Java has already
 * decoded its own copies as ASCII, losing every other character.
 */
internal fun relaunchUnderUtf8LocaleIfNeeded() {
	if (!needsUtf8Relaunch(hostOs, System.getProperty("sun.jnu.encoding"), System.getenv(RELAUNCHED_ENV) != null)) return
	@Suppress("TooGenericExceptionCaught") // any failure leaves Hammer running as it is, which is no worse
	try {
		val argv = splitCmdline(Files.readAllBytes(Paths.get("/proc/self/cmdline")))
		val libc = Native.load("c", LibC::class.java)
		val locale = UTF8_LOCALES.firstOrNull { libc.hasLocale(it) } ?: return
		libc.setenv("LC_CTYPE", locale, 1)
		// LC_ALL outranks LC_CTYPE. Left unset, the user's LANG keeps the language and formats.
		if (!System.getenv("LC_ALL").isNullOrEmpty()) libc.setenv("LC_ALL", locale, 1)
		libc.setenv(RELAUNCHED_ENV, "1", 1)
		// The jpackage launcher marks its process with this; a process that inherits it is taken for the
		// launcher's own JVM restart, and its arguments for java's, so `mcp` becomes a main class.
		libc.unsetenv(JPACKAGE_LAUNCHER_ENV)
		libc.execv("/proc/self/exe", nativeArgv(argv))
		System.err.println("Hammer could not restart under $locale (errno ${Native.getLastError()})")
	} catch (t: Throwable) {
		System.err.println("Hammer could not restart under a UTF-8 locale: $t")
	}
}

/** [alreadyRelaunched] stops a loop if the restart is somehow no better off. */
internal fun needsUtf8Relaunch(os: HostOs, fileNameEncoding: String?, alreadyRelaunched: Boolean): Boolean =
	os == HostOs.Linux &&
		!alreadyRelaunched &&
		fileNameEncoding != null &&
		!fileNameEncoding.equals("UTF-8", ignoreCase = true)

/** The arguments in a `/proc/<pid>/cmdline`, each ended by a NUL; an empty argument is two NULs in a row. */
internal fun splitCmdline(cmdline: ByteArray): List<ByteArray> {
	val args = mutableListOf<ByteArray>()
	var start = 0
	for (i in cmdline.indices) {
		if (cmdline[i] == 0.toByte()) {
			args += cmdline.copyOfRange(start, i)
			start = i + 1
		}
	}
	if (start < cmdline.size) args += cmdline.copyOfRange(start, cmdline.size)
	return args
}

/** NUL-terminated copies of [args], in a NULL-terminated array, as `execv` takes them. */
private fun nativeArgv(args: List<ByteArray>): Array<Pointer?> {
	val pointers: List<Pointer?> = args.map { arg ->
		Memory(arg.size + 1L).apply {
			write(0, arg, 0, arg.size)
			setByte(arg.size.toLong(), 0)
		}
	}
	return (pointers + null).toTypedArray()
}

private interface LibC : Library {
	fun setenv(name: String, value: String, overwrite: Int): Int
	fun unsetenv(name: String): Int
	fun setlocale(category: Int, locale: String?): String?
	fun execv(path: String, argv: Array<Pointer?>): Int
}

/** Whether [name] is installed, found by switching to it and back. */
private fun LibC.hasLocale(name: String): Boolean {
	val current = setlocale(LC_CTYPE, null) ?: return false
	val found = setlocale(LC_CTYPE, name) != null
	setlocale(LC_CTYPE, current)
	return found
}

// C.UTF-8 is missing from glibc before 2.35 on some distributions, such as RHEL.
private val UTF8_LOCALES = listOf("C.UTF-8", "en_US.UTF-8")
private const val LC_CTYPE = 0
private const val RELAUNCHED_ENV = "HAMMER_UTF8_RELAUNCHED"
private const val JPACKAGE_LAUNCHER_ENV = "_JPACKAGE_LAUNCHER"
