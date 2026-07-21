package com.darkrockstudios.apps.hammer.e2e.util

import okio.Buffer
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Sink
import okio.Source
import okio.Timeout

/**
 * Serializes every access to a [FileSystem] that more than one thread can touch.
 *
 * [okio.fakefilesystem.FakeFileSystem] is not thread-safe: it tracks open files in a plain
 * `ArrayList`, so an open on one thread while another iterates that list throws
 * `ConcurrentModificationException`. In an end-to-end test the fake is shared by the test thread,
 * the client's dispatcher threads and the server's Jetty threads, and that collision surfaces as a
 * failed sync in whichever test is unlucky.
 *
 * The lock covers the returned [Source], [Sink] and [FileHandle] too — the fake mutates its open-file
 * list when those close, not only when they open.
 */
class SynchronizedFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {

	private val lock = Any()

	override fun canonicalize(path: Path): Path = synchronized(lock) { super.canonicalize(path) }

	override fun metadataOrNull(path: Path): FileMetadata? = synchronized(lock) { super.metadataOrNull(path) }

	override fun list(dir: Path): List<Path> = synchronized(lock) { super.list(dir) }

	override fun listOrNull(dir: Path): List<Path>? = synchronized(lock) { super.listOrNull(dir) }

	override fun openReadOnly(file: Path): FileHandle =
		synchronized(lock) { LockedFileHandle(super.openReadOnly(file), readWrite = false) }

	override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle =
		synchronized(lock) {
			LockedFileHandle(super.openReadWrite(file, mustCreate, mustExist), readWrite = true)
		}

	override fun source(file: Path): Source = synchronized(lock) { LockedSource(super.source(file)) }

	override fun sink(file: Path, mustCreate: Boolean): Sink =
		synchronized(lock) { LockedSink(super.sink(file, mustCreate)) }

	override fun appendingSink(file: Path, mustExist: Boolean): Sink =
		synchronized(lock) { LockedSink(super.appendingSink(file, mustExist)) }

	override fun createDirectory(dir: Path, mustCreate: Boolean) =
		synchronized(lock) { super.createDirectory(dir, mustCreate) }

	override fun atomicMove(source: Path, target: Path) = synchronized(lock) { super.atomicMove(source, target) }

	override fun delete(path: Path, mustExist: Boolean) = synchronized(lock) { super.delete(path, mustExist) }

	override fun createSymlink(source: Path, target: Path) = synchronized(lock) { super.createSymlink(source, target) }

	private inner class LockedSource(private val delegate: Source) : Source {
		override fun read(sink: Buffer, byteCount: Long): Long = synchronized(lock) { delegate.read(sink, byteCount) }
		override fun timeout(): Timeout = delegate.timeout()
		override fun close() = synchronized(lock) { delegate.close() }
	}

	private inner class LockedSink(private val delegate: Sink) : Sink {
		override fun write(source: Buffer, byteCount: Long) = synchronized(lock) { delegate.write(source, byteCount) }
		override fun flush() = synchronized(lock) { delegate.flush() }
		override fun timeout(): Timeout = delegate.timeout()
		override fun close() = synchronized(lock) { delegate.close() }
	}

	private inner class LockedFileHandle(
		private val delegate: FileHandle,
		readWrite: Boolean,
	) : FileHandle(readWrite) {
		override fun protectedSize(): Long = synchronized(lock) { delegate.size() }

		override fun protectedRead(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int): Int =
			synchronized(lock) { delegate.read(fileOffset, array, arrayOffset, byteCount) }

		override fun protectedWrite(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int) =
			synchronized(lock) { delegate.write(fileOffset, array, arrayOffset, byteCount) }

		override fun protectedFlush() = synchronized(lock) { delegate.flush() }

		override fun protectedResize(size: Long) = synchronized(lock) { delegate.resize(size) }

		override fun protectedClose() = synchronized(lock) { delegate.close() }
	}
}
