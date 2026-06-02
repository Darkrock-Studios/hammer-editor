package com.darkrockstudios.apps.hammer.common.util.zip

import no.synth.kmpzip.okio.unzipFrom
import no.synth.kmpzip.okio.zipTo
import no.synth.kmpzip.zip.ZipInputStream
import no.synth.kmpzip.zip.safeEntrySegments
import okio.FileSystem
import okio.Path
import okio.buffer

private const val COPY_BUFFER_SIZE = 8192

/**
 * Creates a zip archive from a directory. The archive contains entries rooted at
 * [sourceDirectory]'s name, matching the structure produced by the previous
 * implementation.
 */
suspend fun zipDirectory(
	fileSystem: FileSystem,
	sourceDirectory: Path,
	destinationZip: Path,
) {
	fileSystem.zipTo(
		target = destinationZip,
		sources = listOf(sourceDirectory),
	)
}

/**
 * Extracts a zip archive to a directory.
 */
suspend fun unzipToDirectory(
	fileSystem: FileSystem,
	zipPath: Path,
	destinationDirectory: Path,
) {
	fileSystem.unzipFrom(
		archive = zipPath,
		target = destinationDirectory,
	)
}

/**
 * Extracts a zip archive held in memory to a directory. Kept non-suspend because
 * its only caller — `ExampleProjectRepository.platformInstall` — is not suspend.
 */
fun unzipBytesToDirectory(
	fileSystem: FileSystem,
	zipBytes: ByteArray,
	destinationDirectory: Path,
) {
	fileSystem.createDirectories(destinationDirectory)
	ZipInputStream(zipBytes).use { zis ->
		val buffer = ByteArray(COPY_BUFFER_SIZE)
		while (true) {
			val entry = zis.nextEntry ?: break
			val target = safeEntrySegments(entry.name)
				.fold(destinationDirectory) { acc, segment -> acc / segment }
			if (entry.isDirectory) {
				fileSystem.createDirectories(target)
			} else {
				target.parent?.let { fileSystem.createDirectories(it) }
				fileSystem.sink(target).buffer().use { sink ->
					while (true) {
						val n = zis.read(buffer, 0, buffer.size)
						if (n == -1) break
						sink.write(buffer, 0, n)
					}
				}
			}
		}
	}
}
