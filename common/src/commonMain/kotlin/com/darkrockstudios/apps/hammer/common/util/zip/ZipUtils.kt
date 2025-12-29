package com.darkrockstudios.apps.hammer.common.util.zip

import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.forEachEntry
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlinx.io.files.Path as KioPath
import okio.Path as OkioPath

/**
 * Creates a zip archive from a directory.
 *
 * @param fileSystem The Okio FileSystem
 * @param sourceDirectory The directory to compress
 * @param destinationZip The path for the output zip file
 * @param skipHiddenFiles Whether to skip hidden files (files starting with ".")
 */
fun zipDirectory(
	fileSystem: FileSystem,
	sourceDirectory: OkioPath,
	destinationZip: OkioPath,
	skipHiddenFiles: Boolean = true
) {
	val kioDestZip = destinationZip.toKioPath()

	Zip.open(kioDestZip, mode = Zip.Mode.Write).use { zip ->
		addDirectoryToZip(fileSystem, sourceDirectory, zip, sourceDirectory, skipHiddenFiles)
	}
}

/**
 * Recursively adds a directory to a zip archive, optionally skipping hidden files.
 */
private fun addDirectoryToZip(
	fileSystem: FileSystem,
	currentPath: OkioPath,
	zip: Zip,
	rootPath: OkioPath,
	skipHiddenFiles: Boolean
) {
	fileSystem.list(currentPath).forEach { childPath ->
		val name = childPath.name

		// Skip hidden files if requested
		if (skipHiddenFiles && name.startsWith(".")) {
			return@forEach
		}

		val metadata = fileSystem.metadata(childPath)
		val relativePath = childPath.relativeTo(rootPath)
		val entryPath = KioPath(rootPath.name, relativePath.toString())

		if (metadata.isDirectory) {
			// Recursively process subdirectories
			addDirectoryToZip(fileSystem, childPath, zip, rootPath, skipHiddenFiles)
		} else if (metadata.isRegularFile) {
			// Add file entry
			fileSystem.read(childPath) {
				val bytes = readByteArray()
				val kioSource = kotlinx.io.Buffer().apply { write(bytes) }
				zip.entryFromSource(entryPath, kioSource)
			}
		}
	}
}

/**
 * Extracts a zip archive to a directory.
 *
 * @param fileSystem The Okio FileSystem
 * @param zipPath The path to the zip file to extract
 * @param destinationDirectory The directory to extract to
 */
fun unzipToDirectory(
	fileSystem: FileSystem,
	zipPath: OkioPath,
	destinationDirectory: OkioPath
) {
	val kioZipPath = zipPath.toKioPath()

	Zip.open(kioZipPath).use { zip ->
		zip.forEachEntry { entry ->
			val targetPath = destinationDirectory / entry.path.toString()
			if (entry.isDirectory) {
				fileSystem.createDirectories(targetPath)
			} else {
				targetPath.parent?.let { fileSystem.createDirectories(it) }
				fileSystem.write(targetPath) {
					write(entry.readToBytes())
				}
			}
		}
	}
}

/**
 * Extracts a zip archive from a byte array to a directory.
 *
 * @param fileSystem The Okio FileSystem used to create a temporary file
 * @param zipBytes The zip file contents as a byte array
 * @param destinationDirectory The directory to extract to
 */
fun unzipBytesToDirectory(
	fileSystem: FileSystem,
	zipBytes: ByteArray,
	destinationDirectory: OkioPath
) {
	// Create a temporary file to hold the zip bytes
	val tempZipPath = destinationDirectory.parent?.let { parent ->
		parent / ".temp_extract_${kotlin.random.Random.nextLong()}.zip"
	} ?: throw IllegalArgumentException("Destination directory must have a parent")

	try {
		// Write bytes to temp file
		fileSystem.write(tempZipPath) {
			write(zipBytes)
		}

		// Extract from temp file
		unzipToDirectory(fileSystem, tempZipPath, destinationDirectory)
	} finally {
		// Clean up temp file
		fileSystem.delete(tempZipPath)
	}
}

/**
 * Converts an Okio Path to a kotlinx.io Path.
 */
fun OkioPath.toKioPath(): KioPath = KioPath(this.toString())

/**
 * Converts a relative Okio Path to a string suitable for zip entry paths.
 */
private fun OkioPath.relativeTo(basePath: OkioPath): OkioPath {
	val baseStr = basePath.toString()
	val thisStr = this.toString()

	return if (thisStr.startsWith(baseStr)) {
		val relative = thisStr.removePrefix(baseStr).removePrefix("/").removePrefix("\\")
		OkioPath(relative)
	} else {
		this
	}
}

/**
 * Creates an Okio Path from a string.
 */
private fun OkioPath(pathString: String): OkioPath = pathString.toPath()
