package com.darkrockstudios.apps.hammer.common.fileio.okio

import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Sink

/**
 * Fail-closed containment guard for filesystem mutations. Every write, append,
 * move, delete, or directory creation routed through this filesystem must land
 * within one of [allowedRoots]; anything else throws.
 *
 * Reads, listings, and metadata are deliberately not guarded — they are not the
 * traversal hazard and gating them would break legitimate inspection of paths
 * outside the managed roots.
 *
 * [allowedRoots] is a lambda re-evaluated on every check: the projects directory
 * is user-relocatable at runtime (external-storage toggle, dev mode, app-store
 * sandbox), so a snapshot taken at construction time would go stale.
 *
 * External, user-chosen write targets (e.g. exporting a story to a folder the
 * user picked) must bypass this guard by using the raw platform filesystem
 * directly, not this decorator.
 */
class ContainedFileSystem(
	delegate: FileSystem,
	private val allowedRoots: () -> List<Path>,
) : ForwardingFileSystem(delegate) {

	override fun sink(file: Path, mustCreate: Boolean): Sink {
		requireContained(file, "sink")
		return super.sink(file, mustCreate)
	}

	override fun appendingSink(file: Path, mustExist: Boolean): Sink {
		requireContained(file, "appendingSink")
		return super.appendingSink(file, mustExist)
	}

	override fun atomicMove(source: Path, target: Path) {
		requireContained(source, "atomicMove(source)")
		requireContained(target, "atomicMove(target)")
		super.atomicMove(source, target)
	}

	override fun delete(path: Path, mustExist: Boolean) {
		requireContained(path, "delete")
		super.delete(path, mustExist)
	}

	override fun createDirectory(dir: Path, mustCreate: Boolean) {
		requireDirectoryAllowed(dir)
		super.createDirectory(dir, mustCreate)
	}

	override fun createSymlink(source: Path, target: Path) {
		requireContained(source, "createSymlink")
		super.createSymlink(source, target)
	}

	override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
		requireContained(file, "openReadWrite")
		return super.openReadWrite(file, mustCreate, mustExist)
	}

	private fun requireContained(path: Path, operation: String) {
		val roots = allowedRoots()
		if (roots.none { path.isWithin(it) }) {
			throw ContainmentViolationException(
				"Refusing to $operation outside the app's managed storage: $path"
			)
		}
	}

	/**
	 * Also permits a root's ancestors: `createDirectories` scaffolds a root's missing
	 * parents one at a time on first run. Sibling escapes stay blocked.
	 */
	private fun requireDirectoryAllowed(dir: Path) {
		val roots = allowedRoots()
		val allowed = roots.any { root -> dir.isWithin(root) || root.isWithin(dir) }
		if (!allowed) {
			throw ContainmentViolationException(
				"Refusing to create a directory outside the app's managed storage: $dir"
			)
		}
	}
}

class ContainmentViolationException(message: String) : okio.IOException(message)
