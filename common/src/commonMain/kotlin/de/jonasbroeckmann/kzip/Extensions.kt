package de.jonasbroeckmann.kzip

import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Iterates over all entries in the ZIP file and executes the given block on each index and entry.
 *
 * @param block the block to execute on each index and entry
 */
public inline fun Zip.forEachEntryIndexed(crossinline block: (Int, Zip.Entry) -> Unit) {
	for (i in 0 until numberOfEntries) {
		entry(i) { block(i, this) }
	}
}

/**
 * Iterates over all entries in the ZIP file and executes the given block on each entry.
 *
 * @param block the block to execute on each entry
 */
public inline fun Zip.forEachEntry(crossinline block: (Zip.Entry) -> Unit): Unit = forEachEntryIndexed { _, entry ->
	block(entry)
}

/**
 * Extracts all entries in the ZIP file into the given directory.
 *
 * @param directory the directory to extract the entries into
 */
public fun Zip.extractTo(directory: Path) {
	forEachEntry { entry ->
		val target = Path(directory, entry.path.toString())
		if (entry.isDirectory) {
			SystemFileSystem.createDirectories(target)
		} else {
			target.parent?.let { SystemFileSystem.createDirectories(it) }
			entry.readToPath(target)
		}
	}
}

/**
 * Compresses the given path into the ZIP file.
 * If the path is a file, a file entry is added.
 * If the path is a directory, a folder entry is added and all children are compressed recursively.
 *
 * @param path the path to compress
 * @param pathInZip the path in the ZIP file to compress the path to
 */
public fun Zip.compressFrom(path: Path, pathInZip: Path? = null) {
	val metadata = SystemFileSystem.metadataOrNull(path) ?: throw IOException("Cannot read metadata of $path")
	if (metadata.isDirectory) {
		if (pathInZip != null) folderEntry(pathInZip)
		SystemFileSystem.list(path).forEach { child ->
			compressFrom(child, pathInZip?.let { Path(it, child.name) } ?: Path(child.name))
		}
	} else if (metadata.isRegularFile) {
		if (pathInZip != null) entryFromPath(pathInZip, path)
	} else {
		throw UnsupportedOperationException("Unsupported file type at $path")
	}
}
