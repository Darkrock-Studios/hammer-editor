package com.darkrockstudios.apps.hammer.desktop

import java.io.File
import java.security.MessageDigest

/** Set by packaged builds to the directory the JVM writes its class data archive into. */
private const val ARCHIVE_DIR_PROPERTY = "hammer.cds.dir"
private const val ARCHIVE = "hammer.jsa"

/** Who wrote the archive, the install it was written by, and a fingerprint of that install's jars. */
private const val ARCHIVE_INFO = "hammer.jsa.info"
private const val WINDOW = "window"
private const val CLI = "cli"

private fun archiveDir(): File? = System.getProperty(ARCHIVE_DIR_PROPERTY)?.let(::File)

private fun classPath(): String = System.getProperty("java.class.path").orEmpty()

/** The AppImage mounts somewhere new each launch, so its own file names the install instead. */
private fun install(): String = System.getenv("APPIMAGE") ?: System.getProperty("jpackage.app-path").orEmpty()

/**
 * Packaged builds run with `-XX:+AutoCreateSharedArchive`, which writes the archive at exit when none
 * was found at start. The JVM does not create its directory, and when it cannot write the archive it
 * exits with status 1 and an error on stdout, so every launch makes the directory first. A CLI run
 * that will write the archive says so, as it holds only the CLI's classes (on Linux and macOS the CLI
 * shares the window's launcher).
 */
internal fun prepareClassDataArchive(
	isCli: Boolean,
	dir: File? = archiveDir(),
	install: String = install(),
	classPath: String = classPath(),
) {
	if (dir == null) return
	dir.mkdirs()
	if (isCli && !File(dir, ARCHIVE).exists()) writeInfo(dir, ArchiveInfo(CLI, install, fingerprint(classPath)))
}

/**
 * Run once this launch is the window, not one handing its arguments to a running window. Deletes an
 * archive of this install that will not serve the window, so its next exit writes a full one: one the
 * CLI wrote, or one whose jars have changed, which the JVM ignores without replacing. Archives of other
 * installs that still exist are left alone, as are their version directories.
 */
internal fun adoptClassDataArchive(
	dir: File? = archiveDir(),
	install: String = install(),
	classPath: String = classPath(),
) {
	if (dir == null) return
	dir.parentFile?.listFiles()
		?.filter { it != dir && readInfo(it)?.isFrom(install) != false }
		?.forEach { it.deleteRecursively() }
	val archive = File(dir, ARCHIVE)
	val info = readInfo(dir)
	val fingerprint = fingerprint(classPath)
	if (!archive.exists()) {
		// A CLI run that is still going will write the archive after this one, so its claim stands.
		if (info?.writer != CLI) writeInfo(dir, ArchiveInfo(WINDOW, install, fingerprint))
		return
	}
	if (info != null && !info.isFrom(install)) return
	if (info?.writer == WINDOW && info.fingerprint == fingerprint) return
	// Windows cannot delete the archive this run has mapped; the info stays so the next run tries again.
	if (archive.delete()) File(dir, ARCHIVE_INFO).delete()
}

private data class ArchiveInfo(val writer: String, val install: String, val fingerprint: String) {
	/** True for this install, or for one that is gone, such as a moved or uninstalled copy. */
	fun isFrom(other: String): Boolean = install == other || install.isEmpty() || !File(install).exists()
}

private fun readInfo(dir: File): ArchiveInfo? =
	runCatching { File(dir, ARCHIVE_INFO).readLines() }.getOrNull()
		?.takeIf { it.size == 3 }
		?.let { (writer, install, fingerprint) -> ArchiveInfo(writer, install, fingerprint) }

private fun writeInfo(dir: File, info: ArchiveInfo) {
	runCatching { File(dir, ARCHIVE_INFO).writeText("${info.writer}\n${info.install}\n${info.fingerprint}") }
}

/**
 * The JVM rejects an archive when a jar's size or modification time differs, but accepts the same jars
 * at another path.
 */
private fun fingerprint(classPath: String): String {
	val digest = MessageDigest.getInstance("SHA-256")
	classPath.split(File.pathSeparator).filter { it.isNotEmpty() }.forEach { entry ->
		val file = File(entry)
		digest.update("${file.name} ${file.length()} ${file.lastModified()}\n".toByteArray())
	}
	return digest.digest().joinToString("") { "%02x".format(it) }
}
