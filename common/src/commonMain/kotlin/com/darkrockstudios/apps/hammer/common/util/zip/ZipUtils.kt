package com.darkrockstudios.apps.hammer.common.util.zip

import no.synth.kmpzip.okio.unzipFrom
import no.synth.kmpzip.okio.zipTo
import no.synth.kmpzip.zip.ZipConstants
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipInputStream
import no.synth.kmpzip.zip.safeEntrySegments
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import no.synth.kmpzip.okio.ZipOutputStream as OkioZipOutputStream

private const val COPY_BUFFER_SIZE = 8192

/** One file of an archive [writeZip] builds in memory. */
class InMemoryZipEntry(val name: String, val bytes: ByteArray)

/**
 * Writes [entries] to [sink] as a zip archive, in order, and flushes it; [sink] is left open.
 *
 * [stored] writes every entry uncompressed with its sizes and CRC in the local header, as Word does.
 * kmp-zip always defers a deflated entry's sizes to a data descriptor, which some readers mishandle.
 */
fun writeZip(sink: BufferedSink, entries: List<InMemoryZipEntry>, stored: Boolean = false) {
	val zip = OkioZipOutputStream(sink)
	for (entry in entries) {
		val zipEntry = ZipEntry(entry.name)
		if (stored) {
			zipEntry.method = ZipConstants.STORED
			zipEntry.size = entry.bytes.size.toLong()
			zipEntry.compressedSize = entry.bytes.size.toLong()
			zipEntry.crc = crc32(entry.bytes)
		}
		zip.putNextEntry(zipEntry)
		zip.write(entry.bytes)
		zip.closeEntry()
	}
	zip.finish()
	zip.flush()
}

private val CRC32_TABLE = IntArray(256) { n ->
	var c = n
	repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
	c
}

/** The zip format's CRC-32, which kmp-zip computes but does not expose. */
private fun crc32(bytes: ByteArray): Long {
	var crc = -1
	for (b in bytes) crc = CRC32_TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
	return crc.inv().toLong() and 0xFFFFFFFFL
}

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
				fileSystem.write(target) {
					while (true) {
						val n = zis.read(buffer, 0, buffer.size)
						if (n == -1) break
						write(buffer, 0, n)
					}
				}
			}
		}
	}
}
