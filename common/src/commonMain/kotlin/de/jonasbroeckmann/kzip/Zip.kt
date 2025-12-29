package de.jonasbroeckmann.kzip

import de.jonasbroeckmann.kzip.Zip.Companion.open
import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.deflating
import dev.karmakrafts.kompress.inflating
import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal const val LOCAL_FILE_HEADER_SIG = 0x04034b50L
internal const val CENTRAL_DIRECTORY_HEADER_SIG = 0x02014b50L
internal const val EOCD_SIG = 0x06054b50L

internal const val METHOD_STORED = 0
internal const val METHOD_DEFLATED = 8

internal sealed interface DataSource {
	class Memory(val bytes: ByteArray) : DataSource
	class File(val path: Path) : DataSource
	class ZipEntry(
		val zipPath: Path,
		val localHeaderOffset: Long,
		val compressedSize: Long,
		val method: Int
	) : DataSource
}

internal class BoundedSource(
	private val source: Source,
	private var remaining: Long
) : RawSource {
	override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
		if (remaining <= 0L) return -1
		val toRead = minOf(byteCount, remaining)
		val read = source.readAtMostTo(sink, toRead)
		if (read > 0) remaining -= read
		return read
	}

	override fun close() = source.close()
}

/**
 * A ZIP file.
 *
 * Use the [open] function to open a ZIP file.
 */
public class Zip(
	public val path: Path,
	public val mode: Mode,
	public val level: CompressionLevel = CompressionLevel.Default
) : AutoCloseable {
	private val entries = mutableListOf<ZipEntry>()
	private var isClosed = false

	init {
		if (mode == Mode.Read || mode == Mode.Append) {
			readCentralDirectory()
		}
	}

	/**
	 * The number of entries in the ZIP file.
	 */
	public val numberOfEntries: Int
		get() = entries.size

	private fun readCentralDirectory() {
		val size = SystemFileSystem.metadataOrNull(path)?.size ?: throw ZipException("File not found: $path")
		if (size < 22) throw ZipException("Not a ZIP file (too small)")

		SystemFileSystem.source(path).use { rawSource ->
			rawSource.buffered().use { source ->
				val searchSize = minOf(size, 65535L + 22L)
				source.skip(size - searchSize)
				val bytes = source.readByteArray()

				var eocdOffsetInBytes = -1
				for (i in bytes.size - 22 downTo 0) {
					if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4b.toByte() &&
						bytes[i + 2] == 0x05.toByte() && bytes[i + 3] == 0x06.toByte()
					) {
						eocdOffsetInBytes = i
						break
					}
				}
				if (eocdOffsetInBytes == -1) throw ZipException("Not a ZIP file (EOCD not found)")

				val eocdBuffer = Buffer().apply { write(bytes, eocdOffsetInBytes, bytes.size) }
				eocdBuffer.readIntLe() // sig
				eocdBuffer.readShortLe() // disk number
				eocdBuffer.readShortLe() // disk start
				eocdBuffer.readShortLe() // entries on disk
				val totalEntries = eocdBuffer.readShortLe().toInt() and 0xFFFF
				val cdSize = eocdBuffer.readIntLe().toLong() and 0xFFFFFFFFL
				val cdOffset = eocdBuffer.readIntLe().toLong() and 0xFFFFFFFFL

				openSourceAt(path, cdOffset).use { cdRawSource ->
					cdRawSource.buffered().use { cdSource ->
						for (i in 0 until totalEntries) {
							val sig = cdSource.readIntLe().toLong() and 0xFFFFFFFFL
							if (sig != CENTRAL_DIRECTORY_HEADER_SIG) throw ZipException("Invalid Central Directory Header signature at ${cdOffset + i * 46}")

							cdSource.readShortLe() // version made by
							cdSource.readShortLe() // version needed
							val flags = cdSource.readShortLe().toInt() and 0xFFFF
							val method = cdSource.readShortLe().toInt() and 0xFFFF
							cdSource.readShortLe() // mod time
							cdSource.readShortLe() // mod date
							val crc = cdSource.readIntLe().toLong() and 0xFFFFFFFFL
							val compressedSize = cdSource.readIntLe().toLong() and 0xFFFFFFFFL
							val uncompressedSize = cdSource.readIntLe().toLong() and 0xFFFFFFFFL
							val nameLen = cdSource.readShortLe().toInt() and 0xFFFF
							val extraLen = cdSource.readShortLe().toInt() and 0xFFFF
							val commentLen = cdSource.readShortLe().toInt() and 0xFFFF
							cdSource.readShortLe() // disk start
							cdSource.readShortLe() // internal attr
							cdSource.readIntLe() // external attr
							val localHeaderOffset = cdSource.readIntLe().toLong() and 0xFFFFFFFFL

							val name = cdSource.readString(nameLen.toLong())
							cdSource.skip(extraLen.toLong() + commentLen.toLong())

							entries.add(
								ZipEntry(
									this,
									name,
									method,
									compressedSize.toULong(),
									uncompressedSize.toULong(),
									crc,
									localHeaderOffset,
									DataSource.ZipEntry(path, localHeaderOffset, compressedSize, method)
								)
							)
						}
					}
				}
			}
		}
	}

	/**
	 * Obtains an entry in the ZIP file by its path.
	 *
	 * @param entry the path of the entry
	 * @param block the block to execute on the entry
	 */
	public fun entry(entry: Path, block: Entry.() -> Unit) {
		val name = Zip.pathToEntryName(entry)
		val folderName = Zip.pathToFolderEntryName(entry)
		val e = entries.find { it.pathName == name || it.pathName == folderName }
			?: throw ZipException("Entry not found: $name")
		e.block()
	}

	/**
	 * Obtains an entry in the ZIP file by its index.
	 *
	 * @param index the index of the entry
	 * @param block the block to execute on the entry
	 */
	public fun entry(index: Int, block: Entry.() -> Unit) {
		if (index !in entries.indices) throw ZipException("Invalid index: $index")
		entries[index].block()
	}

	/**
	 * Deletes entries from the ZIP file.
	 *
	 * @param paths the paths of the entries to delete
	 */
	public fun deleteEntries(paths: List<Path>) {
		requireWritable()
		val names = paths.map { Zip.pathToEntryName(it) }
		entries.removeAll { it.pathName in names }
	}

	/**
	 * Deletes entries from the ZIP file.
	 *
	 * @param indices the indices of the entries to delete
	 */
	public fun deleteEntriesByIndex(indices: List<Int>) {
		requireWritable()
		val sortedIndices = indices.sortedDescending()
		for (index in sortedIndices) {
			if (index in entries.indices) {
				entries.removeAt(index)
			}
		}
	}

	/**
	 * Adds an entry to the ZIP file from a [Source].
	 *
	 * @param entry the path of the entry
	 * @param data the source to read the entry from
	 */
	public fun entryFromSource(entry: Path, data: Source) {
		requireWritable()
		val bytes = data.readByteArray()
		addEntry(Zip.pathToEntryName(entry), DataSource.Memory(bytes), bytes.size.toULong(), false)
	}

	/**
	 * Adds an entry to the ZIP file from a file.
	 *
	 * @param entry the path of the entry
	 * @param file the path to the file to read the entry from
	 */
	public fun entryFromPath(entry: Path, file: Path) {
		requireWritable()
		file.requireFile()
		val size = SystemFileSystem.metadataOrNull(file)?.size ?: 0L
		addEntry(Zip.pathToEntryName(entry), DataSource.File(file), size.toULong(), false)
	}

	/**
	 * Adds a folder entry to the ZIP file.
	 *
	 * @param entry the path of the folder entry
	 */
	public fun folderEntry(entry: Path) {
		requireWritable()
		addEntry(Zip.pathToFolderEntryName(entry), DataSource.Memory(byteArrayOf()), 0uL, true)
	}

	private fun addEntry(name: String, dataSource: DataSource, size: ULong, isDirectory: Boolean) {
		val existing = entries.find { it.pathName == name }
		// Determine method based on level and type
		val method = if (isDirectory || level == CompressionLevel.NoCompression) METHOD_STORED else METHOD_DEFLATED

		val newEntry = ZipEntry(
			this,
			name,
			method,
			if (method == METHOD_STORED) size else 0uL, // compressed size updated during write
			size,
			0,
			0,
			dataSource,
			isDirectory
		)

		if (existing != null) {
			entries[entries.indexOf(existing)] = newEntry
		} else {
			entries.add(newEntry)
		}
	}

	override fun close() {
		if (isClosed) return
		if (mode != Mode.Read) {
			writeZip()
		}
		isClosed = true
	}

	private fun writeZip() {
		val tempPath = Path(path.toString() + ".tmp")
		try {
			SystemFileSystem.sink(tempPath).buffered().use { sink ->
				val cdEntries = mutableListOf<ZipEntry>()
				var currentOffset = 0L

				for (entry in entries) {
					val offset = currentOffset
					// writeEntryData now returns CRC, CompressedSize, and UncompressedSize
					val (crc, compSize, uncompSize) = writeEntryData(entry, sink)

					val entryWithOffset = entry.copy(
						localHeaderOffset = offset,
						crc32 = crc,
						compressedSize = compSize,
						uncompressedSize = uncompSize
					).apply {
						this.dataSource = entry.dataSource
						this.isDir = entry.isDir
					}
					cdEntries.add(entryWithOffset)

					val nameBytesSize = entry.pathName.encodeToByteArray().size
					currentOffset += 30 + nameBytesSize + compSize.toLong()
				}

				val cdOffset = currentOffset
				var cdSize = 0L
				for (entry in cdEntries) {
					val nameBytes = entry.pathName.encodeToByteArray()
					sink.writeIntLe(CENTRAL_DIRECTORY_HEADER_SIG.toInt())
					sink.writeShortLe(20) // made by
					sink.writeShortLe(20) // needed
					sink.writeShortLe(0) // flags
					sink.writeShortLe(entry.method.toShort())
					sink.writeShortLe(0) // mod time
					sink.writeShortLe(0) // mod date
					sink.writeIntLe(entry.crc32.toInt())
					sink.writeIntLe(entry.compressedSize.toInt())
					sink.writeIntLe(entry.uncompressedSize.toInt())
					sink.writeShortLe(nameBytes.size.toShort())
					sink.writeShortLe(0) // extra len
					sink.writeShortLe(0) // comment len
					sink.writeShortLe(0) // disk start
					sink.writeShortLe(0) // internal attr
					sink.writeIntLe(0) // external attr
					sink.writeIntLe(entry.localHeaderOffset.toInt())
					sink.write(nameBytes)

					cdSize += 46 + nameBytes.size
				}

				sink.writeIntLe(EOCD_SIG.toInt())
				sink.writeShortLe(0) // disk number
				sink.writeShortLe(0) // disk start
				sink.writeShortLe(cdEntries.size.toShort())
				sink.writeShortLe(cdEntries.size.toShort())
				sink.writeIntLe(cdSize.toInt())
				sink.writeIntLe(cdOffset.toInt())
				sink.writeShortLe(0) // comment len
			}
			SystemFileSystem.atomicMove(tempPath, path)
		} finally {
			if (SystemFileSystem.exists(tempPath)) SystemFileSystem.delete(tempPath)
		}
	}

	private fun writeEntryData(entry: ZipEntry, sink: Sink): Triple<Long, ULong, ULong> {
		val dataSource = entry.dataSource!!
		val finalCrc: Long
		val uncompSize: ULong

		// Pass 1: Calculate CRC and Uncompressed Size
		if (dataSource is DataSource.Memory) {
			finalCrc = Crc32.calculate(dataSource.bytes)
			uncompSize = dataSource.bytes.size.toULong()
		} else {
			val crc = Crc32()
			var size = 0uL
			val s1 = when (dataSource) {
				is DataSource.File -> openSourceAt(dataSource.path, 0L).buffered()
				is DataSource.ZipEntry -> entry.readToSource()
				is DataSource.Memory -> throw IllegalStateException()
			}
			s1.use { s ->
				val buffer = ByteArray(8192)
				while (true) {
					val read = s.readAtMostTo(buffer)
					if (read == -1) break
					crc.update(buffer, 0, read)
					size += read.toULong()
				}
			}
			finalCrc = crc.getValue()
			uncompSize = size
		}

		if (entry.method == METHOD_STORED) {
			// Write Stored data
			val s2 = when (dataSource) {
				is DataSource.Memory -> Buffer().apply { write(dataSource.bytes) }
				is DataSource.File -> openSourceAt(dataSource.path, 0L).buffered()
				is DataSource.ZipEntry -> entry.readToSource()
			}
			s2.use { s ->
				writeLocalHeader(sink, entry.pathName, METHOD_STORED, finalCrc, uncompSize, uncompSize)
				s.transferTo(sink)
			}
			return Triple(finalCrc, uncompSize, uncompSize)
		} else {
			// Write Deflated data
			if (dataSource is DataSource.Memory) {
				// Bulk optimization for memory sources
				val deflated = Deflater.deflate(dataSource.bytes, raw = true, level = level.zlibLevel)
				val compSize = deflated.size.toULong()
				writeLocalHeader(sink, entry.pathName, METHOD_DEFLATED, finalCrc, compSize, uncompSize)
				sink.write(deflated)
				return Triple(finalCrc, compSize, uncompSize)
			} else {
				// Stream to temp file to determine compressed size
				val tempDeflatePath = Path(path.toString() + ".deflate.tmp")
				try {
					val s2 = when (dataSource) {
						is DataSource.File -> openSourceAt(dataSource.path, 0L).buffered()
						is DataSource.ZipEntry -> entry.readToSource()
						is DataSource.Memory -> throw IllegalStateException()
					}
					s2.use { s ->
						SystemFileSystem.sink(tempDeflatePath).buffered().use { tempSink ->
							val deflated = s.deflating(level = level.zlibLevel, raw = true).buffered()
							deflated.use {
								it.transferTo(tempSink)
							}
						}
					}
					val compSize = SystemFileSystem.metadataOrNull(tempDeflatePath)?.size?.toULong() ?: 0uL
					writeLocalHeader(sink, entry.pathName, METHOD_DEFLATED, finalCrc, compSize, uncompSize)
					SystemFileSystem.source(tempDeflatePath).buffered().use { it.transferTo(sink) }
					return Triple(finalCrc, compSize, uncompSize)
				} finally {
					if (SystemFileSystem.exists(tempDeflatePath)) SystemFileSystem.delete(tempDeflatePath)
				}
			}
		}
	}

	private fun writeLocalHeader(sink: Sink, name: String, method: Int, crc: Long, compSize: ULong, uncompSize: ULong) {
		val nameBytes = name.encodeToByteArray()
		sink.writeIntLe(LOCAL_FILE_HEADER_SIG.toInt())
		sink.writeShortLe(20) // Version needed (2.0 for Deflate)
		sink.writeShortLe(0)  // Flags
		sink.writeShortLe(method.toShort())
		sink.writeShortLe(0)  // Mod time
		sink.writeShortLe(0)  // Mod date
		sink.writeIntLe(crc.toInt())
		sink.writeIntLe(compSize.toInt())
		sink.writeIntLe(uncompSize.toInt())
		sink.writeShortLe(nameBytes.size.toShort())
		sink.writeShortLe(0)  // Extra len
		sink.write(nameBytes)
	}

	/**
	 * An entry in a ZIP file. Use the [entry] functions to obtain an entry.
	 */
	public interface Entry {
		/**
		 * The path of the entry.
		 */
		public val path: Path

		/**
		 * Whether the entry is a directory.
		 */
		public val isDirectory: Boolean

		/**
		 * The uncompressed size of the entry in bytes.
		 */
		public val uncompressedSize: ULong

		/**
		 * The compressed size of the entry in bytes.
		 */
		public val compressedSize: ULong

		/**
		 * The CRC-32 checksum of the entry.
		 */
		public val crc32: Long

		/**
		 * Reads the entry to a [Source].
		 *
		 * @return the source to read the entry from
		 */
		public fun readToSource(): Source

		/**
		 * Reads the entry to a byte array.
		 *
		 * @return the byte array to read the entry from
		 */
		public fun readToBytes(): ByteArray

		/**
		 * Reads the entry to a file.
		 *
		 * @param path the path to read the entry to
		 */
		public fun readToPath(path: Path)
	}

	internal data class ZipEntry(
		val zip: Zip,
		val pathName: String,
		val method: Int,
		override val compressedSize: ULong,
		override val uncompressedSize: ULong,
		override val crc32: Long,
		val localHeaderOffset: Long,
		internal var dataSource: DataSource? = null,
		internal var isDir: Boolean = pathName.endsWith('/')
	) : Entry {
		override val path: Path get() = Zip.entryNameToPath(pathName)
		override val isDirectory: Boolean get() = isDir

		override fun readToSource(): Source {
			val rawSource = when (val ds = dataSource!!) {
				is DataSource.Memory -> Buffer().apply { write(ds.bytes) }
				is DataSource.File -> SystemFileSystem.source(ds.path).buffered()
				is DataSource.ZipEntry -> {
					val rawS = openSourceAt(ds.zipPath, ds.localHeaderOffset)
					try {
						val s = rawS.buffered()
						val sig = s.readIntLe().toLong() and 0xFFFFFFFFL
						if (sig != LOCAL_FILE_HEADER_SIG) throw ZipException("Invalid Local File Header at ${ds.localHeaderOffset}")
						s.skip(22)
						val nLen = s.readShortLe().toInt() and 0xFFFF
						val eLen = s.readShortLe().toInt() and 0xFFFF
						s.skip(nLen.toLong() + eLen.toLong())
						val bounded = BoundedSource(s, ds.compressedSize)

						val finalRawSource = if (ds.method == METHOD_DEFLATED) {
							bounded.buffered().inflating(raw = true)
						} else {
							bounded
						}

						return object : RawSource by finalRawSource {
							override fun close() {
								try {
									finalRawSource.close()
								} finally {
									try {
										// bounded doesn't need explicit close if it's finalRawSource
										// but s and rawS definitely do
										s.close()
									} finally {
										rawS.close()
									}
								}
							}
						}.buffered()
					} catch (e: Exception) {
						rawS.close()
						throw e
					}
				}
			}
			return rawSource
		}

		override fun readToBytes(): ByteArray = readToSource().use { it.readByteArray() }

		override fun readToPath(path: Path) {
			readToSource().use { source ->
				SystemFileSystem.sink(path).buffered().use { sink ->
					source.transferTo(sink)
				}
			}
		}
	}

	/**
	 * The mode to open a ZIP file in.
	 */
	public enum class Mode {
		/**
		 * The ZIP file is opened for reading. The file must exist.
		 */
		Read,

		/**
		 * The ZIP file is opened for writing.
		 * If the file exists, it will be truncated.
		 * If the file does not exist, it will be created.
		 */
		Write,

		/**
		 * The ZIP file is opened for appending.
		 */
		Append
	}

	/**
	 * The compression level to use when writing to a ZIP file.
	 * The default is [CompressionLevel.Default].
	 *
	 * @property zlibLevel the zlib compression level
	 */
	public enum class CompressionLevel(public val zlibLevel: Int) {
		NoCompression(0),
		BestSpeed(1),
		BetterSpeed(2),
		GoodSpeed(3),
		MediumBetterSpeed(4),
		Medium(5),
		MediumBetterCompression(6),
		GoodCompression(7),
		BetterCompression(8),
		BestCompression(9);

		public companion object {
			/**
			 * The default compression level.
			 */
			public val Default: CompressionLevel = MediumBetterCompression
		}
	}

	public companion object {
		/**
		 * Opens a ZIP file at the given [path].
		 *
		 * @param path the path to the ZIP file. If the file does not exist, it will be created.
		 * @param mode the mode to open the ZIP file in. Defaults to [Mode.Read]
		 * @param level the compression level to use when writing to the ZIP file. Defaults to [CompressionLevel.Default]
		 */
		public fun open(
			path: Path,
			mode: Mode = Mode.Read,
			level: CompressionLevel = CompressionLevel.Default
		): Zip = Zip(path, mode, level)
	}
}
