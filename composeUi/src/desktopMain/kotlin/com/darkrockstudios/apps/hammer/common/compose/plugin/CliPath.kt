package com.darkrockstudios.apps.hammer.common.compose.plugin

import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.IS_APP_STORE
import com.darkrockstudios.apps.hammer.common.hostOs
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.exists
import kotlin.io.path.readText

/** How `hammer` gets onto the user's PATH for this install. */
sealed interface CliPath {
	/** The package puts it there already, as [command]. */
	data class Provided(val command: String) : CliPath

	/** Hammer can write a script at [file] that runs [launcher]; writing it may take an admin password. */
	data class Script(val file: Path, val launcher: List<String>, val needsAdmin: Boolean) : CliPath {
		val content: String = "#!/bin/sh\n$MARKER\nexec ${launcher.joinToString(" ", transform = ::shellQuoted)} \"\$@\"\n"

		/** Whether [file]'s directory is on [path], a PATH value, so `hammer` runs once the script is written. */
		fun onPath(path: String?): Boolean =
			path.orEmpty().split(File.pathSeparatorChar).any { it.isNotEmpty() && Paths.get(it) == file.parent }
	}

	/** Hammer cannot write it itself; running [command] in a terminal does. */
	data class Manual(val command: String) : CliPath

	/** There is no installed launcher to point at, as in a development run. */
	data object Unavailable : CliPath

	companion object {
		/** Marks a script as Hammer's, so it never overwrites or removes someone else's `hammer`. */
		const val MARKER = "# Written by Hammer; remove it from Hammer's Settings."

		fun detect(
			os: HostOs = hostOs,
			env: Map<String, String> = System.getenv(),
			appPath: String? = System.getProperty("jpackage.app-path"),
			appStore: Boolean = IS_APP_STORE,
			home: Path = Paths.get(System.getProperty("user.home")),
		): CliPath {
			env["SNAP_NAME"]?.let { return Provided(it) }
			env["FLATPAK_ID"]?.let { return Script(home / ".local/bin/hammer", listOf("flatpak", "run", it), needsAdmin = false) }
			if (appPath == null) return Unavailable
			return when (os) {
				HostOs.Linux -> Script(home / ".local/bin/hammer", listOf(appPath), needsAdmin = false)
				HostOs.MacOs -> {
					val script = Script(Paths.get("/usr/local/bin/hammer"), listOf(appPath), needsAdmin = true)
					// The sandbox keeps the app from writing outside its container, with or without a password.
					if (appStore) Manual(script.manualCommand()) else script
				}
				HostOs.Windows, HostOs.Other -> Unavailable
			}
		}

		private operator fun Path.div(other: String): Path = resolve(other)
	}
}

/** A command that writes this script from a terminal. */
fun CliPath.Script.manualCommand(): String {
	val sudo = if (needsAdmin) "sudo " else ""
	val dir = shellQuoted(file.parent.toString())
	val target = shellQuoted(file.toString())
	return "${sudo}mkdir -p $dir && printf '%s\\n' " +
		content.trimEnd('\n').lines().joinToString(" ", transform = ::shellQuoted) +
		" | ${sudo}tee $target > /dev/null && ${sudo}chmod 755 $target"
}

/** What is at a [CliPath.Script]'s file now. */
enum class CliPathState {
	Absent,

	/** Hammer's script, as it would write it now. */
	Installed,

	/** Hammer's script for another launcher, such as one left by an install that has since moved. */
	Stale,

	/** A `hammer` that Hammer did not write, which it leaves alone. */
	Taken,
}

/**
 * Writes and removes a [CliPath.Script]. [runAsAdmin] runs a shell command with an administrator's
 * rights, asking for their password, and says whether it succeeded.
 */
class CliPathInstaller(
	private val runAsAdmin: (shellCommand: String) -> Boolean = ::runAsMacAdmin,
) {
	fun state(script: CliPath.Script): CliPathState {
		val file = script.file
		if (!file.exists() && !Files.isSymbolicLink(file)) return CliPathState.Absent
		val text = try {
			file.readText()
		} catch (e: IOException) {
			return CliPathState.Taken
		}
		return when {
			CliPath.MARKER !in text -> CliPathState.Taken
			text == script.content -> CliPathState.Installed
			else -> CliPathState.Stale
		}
	}

	/** Writes the script, replacing only one of Hammer's own. Throws [IOException] when that fails. */
	fun install(script: CliPath.Script) {
		if (state(script) == CliPathState.Taken) throw IOException("${script.file} is not Hammer's")
		val staged = Files.createTempFile("hammer", ".sh")
		try {
			Files.writeString(staged, script.content)
			if (!tryDirectly { place(staged, script.file) } && !adminInstall(script, staged)) {
				throw IOException("Could not write ${script.file}")
			}
		} finally {
			Files.deleteIfExists(staged)
		}
	}

	/** Removes the script if it is Hammer's. Throws [IOException] when that fails. */
	fun remove(script: CliPath.Script) {
		if (state(script) !in setOf(CliPathState.Installed, CliPathState.Stale)) return
		val removed = tryDirectly { Files.deleteIfExists(script.file) } ||
			(script.needsAdmin && runAsAdmin("rm -f ${shellQuoted(script.file.toString())}"))
		if (!removed) throw IOException("Could not remove ${script.file}")
	}

	private fun place(staged: Path, file: Path) {
		Files.createDirectories(file.parent)
		val next = file.resolveSibling(".${file.fileName}.partial")
		Files.copy(staged, next, StandardCopyOption.REPLACE_EXISTING)
		Files.setPosixFilePermissions(next, PosixFilePermissions.fromString("rwxr-xr-x"))
		Files.move(next, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
	}

	private fun adminInstall(script: CliPath.Script, staged: Path): Boolean {
		if (!script.needsAdmin) return false
		val dir = shellQuoted(script.file.parent.toString())
		val target = shellQuoted(script.file.toString())
		return runAsAdmin("mkdir -p $dir && cp ${shellQuoted(staged.toString())} $target && chmod 755 $target")
	}

	// False, rather than throwing, when the directory needs an administrator.
	private inline fun tryDirectly(block: () -> Unit): Boolean = try {
		block()
		true
	} catch (e: AccessDeniedException) {
		false
	}
}

/** Runs [shellCommand] as root through macOS's own password prompt. */
private fun runAsMacAdmin(shellCommand: String): Boolean {
	val quoted = shellCommand.replace("\\", "\\\\").replace("\"", "\\\"")
	val process = ProcessBuilder("osascript", "-e", "do shell script \"$quoted\" with administrator privileges")
		.redirectErrorStream(true)
		.start()
	process.inputStream.readAllBytes()
	return process.waitFor() == 0
}
