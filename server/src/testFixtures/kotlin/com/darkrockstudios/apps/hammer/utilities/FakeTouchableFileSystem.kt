package com.darkrockstudios.apps.hammer.utilities

import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.fakefilesystem.FakeFileSystem
import kotlin.time.Instant

/**
 * A [TouchableFileSystem] over a [FakeFileSystem]. The fake has no way to set a modification time
 * either, so touched times are held alongside it and overlaid onto [metadataOrNull] — reads and
 * writes of recency therefore agree, which is the whole point of testing against this.
 *
 * Any fresh write drops the overlay, so a rewritten file goes back to the fake's own clock.
 */
class FakeTouchableFileSystem(
	delegate: FileSystem = FakeFileSystem(),
) : TouchableFileSystem(delegate) {

	private val touched = mutableMapOf<Path, Long>()

	override fun setLastModified(path: Path, at: Instant): Boolean {
		if (!exists(path)) return false
		touched[path] = at.toEpochMilliseconds()
		return true
	}

	override fun metadataOrNull(path: Path): FileMetadata? {
		val metadata = super.metadataOrNull(path) ?: return null
		val overlay = touched[path] ?: return metadata
		return FileMetadata(
			isRegularFile = metadata.isRegularFile,
			isDirectory = metadata.isDirectory,
			symlinkTarget = metadata.symlinkTarget,
			size = metadata.size,
			createdAtMillis = metadata.createdAtMillis,
			lastModifiedAtMillis = overlay,
			lastAccessedAtMillis = metadata.lastAccessedAtMillis,
			extras = metadata.extras,
		)
	}

	override fun sink(file: Path, mustCreate: Boolean): Sink {
		touched.remove(file)
		return super.sink(file, mustCreate)
	}

	override fun atomicMove(source: Path, target: Path) {
		super.atomicMove(source, target)
		touched.remove(source)
		touched.remove(target)
	}

	override fun delete(path: Path, mustExist: Boolean) {
		touched.remove(path)
		super.delete(path, mustExist)
	}
}
