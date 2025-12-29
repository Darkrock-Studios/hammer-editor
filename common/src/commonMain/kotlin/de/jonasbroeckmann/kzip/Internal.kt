package de.jonasbroeckmann.kzip

import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

private const val ZipEntryNameSeparator = '/'

internal fun Zip.Companion.pathToEntryName(path: Path): String {
	if (path.isAbsolute) throw IllegalArgumentException("Path for zip entry must be relative: $path")
	path.parent?.let { parent ->
		return "${pathToEntryName(parent)}$ZipEntryNameSeparator${path.name}"
	}
	return path.name
}

internal fun Zip.Companion.pathToFolderEntryName(path: Path): String {
	return "${pathToEntryName(path)}$ZipEntryNameSeparator"
}

internal fun Zip.Companion.entryNameToPath(name: String): Path {
	val elements = name.split(ZipEntryNameSeparator)
	return Path(
		elements.first(),
		*elements.drop(1).let {
			if (it.lastOrNull()?.isEmpty() == true) it.dropLast(1) else it
		}.toTypedArray()
	)
}

internal fun Zip.requireWritable() {
	if (mode !in listOf(Zip.Mode.Write, Zip.Mode.Append)) throw ZipException("Zip is not opened writable")
}

internal fun Zip.requireReadable() {
	if (mode !in listOf(Zip.Mode.Read, Zip.Mode.Append)) throw ZipException("Zip is not opened readable")
}

internal fun Path.requireFile() {
	if (SystemFileSystem.metadataOrNull(this)?.isRegularFile != true) {
		throw IllegalArgumentException("File does not exist or is not a regular file: $this")
	}
}

internal expect fun openSourceAt(path: Path, offset: Long): RawSource
