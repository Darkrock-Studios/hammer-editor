package com.darkrockstudios.apps.hammer.utilities

import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * A [FileSystem] that can also set a file's modification time — the one piece of file mutation okio
 * models on the way in (via [okio.FileMetadata]) but not on the way out.
 *
 * [LruDiskCache] needs it: recency ordering is expressed in modification times, and a cache hit has
 * to refresh one. Everything else about the filesystem forwards to the delegate, so a caller passes
 * this where it would pass a [FileSystem] and a test can still substitute a fake.
 */
abstract class TouchableFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
	/**
	 * Set [path]'s modification time to [at]. Best-effort: returns false when the file is gone or
	 * the filesystem refused, since callers use this for bookkeeping rather than correctness.
	 */
	abstract fun setLastModified(path: Path, at: Instant): Boolean
}

/** The real filesystem. Setting a modification time is standard NIO and works on every host OS. */
class SystemTouchableFileSystem(delegate: FileSystem = FileSystem.SYSTEM) : TouchableFileSystem(delegate) {
	override fun setLastModified(path: Path, at: Instant): Boolean =
		runCatching {
			Files.setLastModifiedTime(path.toNioPath(), FileTime.from(at.toJavaInstant()))
		}.isSuccess
}
