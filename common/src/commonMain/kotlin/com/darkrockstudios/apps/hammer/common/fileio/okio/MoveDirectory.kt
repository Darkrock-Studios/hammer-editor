package com.darkrockstudios.apps.hammer.common.fileio.okio

import okio.FileSystem
import okio.Path

/**
 * Moves the entire contents of [source] into [destination], then removes [source].
 *
 * No-ops when:
 *  - [source] and [destination] are the same directory. Moving a directory onto
 *    itself would copy every file onto itself and then delete it (data loss), and
 *    iterating a listing while deleting from it throws FileNotFoundException.
 *  - [source] does not exist (nothing to move).
 *
 * Individual entries that vanish mid-iteration (a stale listing snapshot) are skipped
 * rather than crashing.
 */
fun FileSystem.moveDirectory(source: Path, destination: Path) {
	if (source == destination) return
	if (!exists(source)) return

	moveContentsRecursively(source, destination)
	deleteRecursively(source)
}

private fun FileSystem.moveContentsRecursively(sourceDir: Path, destinationDir: Path) {
	if (!exists(destinationDir)) {
		createDirectories(destinationDir)
	}

	list(sourceDir).forEach { sourcePath ->
		// A listed entry may already be gone (e.g. a stale snapshot); don't crash.
		if (!exists(sourcePath)) return@forEach

		val destinationPath = destinationDir / sourcePath.name
		if (metadata(sourcePath).isDirectory) {
			moveContentsRecursively(sourcePath, destinationPath)
			delete(sourcePath)
		} else {
			copy(sourcePath, destinationPath)
			delete(sourcePath)
		}
	}
}
