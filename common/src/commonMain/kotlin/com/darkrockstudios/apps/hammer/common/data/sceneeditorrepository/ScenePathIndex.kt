package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource.Companion.validateSceneFilename
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.Path

/**
 * A cached view of the scene files on disk, backed by one recursive [scan] and kept in step with
 * structural changes rather than re-scanning after each one. Everything a caller can ask is derived
 * from that single scan: the name-ordered path list, the path owning a scene id, and how many
 * scenes sit directly inside a directory.
 *
 * Invariants, all maintained under one lock:
 * - [paths] is ordered by file name, matching what [Collection.filterScenePaths] produces.
 * - A scene id maps to the name-first path claiming it, matching [validateScenePaths].
 * - A directory's count is the number of cached paths whose parent it is.
 *
 * Incremental maintenance is an optimisation, never a correctness requirement: any change the
 * index cannot describe exactly drops the cache, and the next read re-scans.
 */
internal class ScenePathIndex(private val scan: () -> List<HPath>) {

	private val lock = SynchronizedObject()

	private var paths: List<HPath>? = null
	private var pathsById: MutableMap<Int, HPath>? = null
	private var childCounts: MutableMap<Path, Int>? = null

	// Duplicate ids are tolerated on disk and resolved by name order across the whole project,
	// which no single-path update can maintain, so their presence forces the re-scan path.
	private var hasDuplicateIds = false

	fun paths(): List<HPath> = synchronized(lock) { pathsLocked() }

	fun pathFor(id: Int): HPath? = synchronized(lock) { pathsByIdLocked()[id] }

	/**
	 * Number of scenes and groups directly inside [parent], or null when the scan holds no entry
	 * for that directory. Null means unknown, not empty: a directory the scan never reached is
	 * indistinguishable here from one that is genuinely empty, and only the caller can tell them
	 * apart by looking at the disk.
	 */
	fun childCountOrNull(parent: HPath): Int? = synchronized(lock) {
		childCountsLocked()[parent.toOkioPath()]
	}

	fun invalidate() = synchronized(lock) { invalidateLocked() }

	fun onCreated(scenePath: HPath) = synchronized(lock) {
		val current = paths ?: return@synchronized
		paths = current.withPathInserted(scenePath)

		pathsById?.let { byId ->
			val id = sceneIdOf(scenePath)
			val owner = byId[id]
			if (owner == null) {
				byId[id] = scenePath
			} else {
				hasDuplicateIds = true
				if (scenePath.name < owner.name) byId[id] = scenePath
			}
		}
		childCounts?.let { counts -> counts.increment(scenePath.toOkioPath().parent) }
	}

	/**
	 * Applies a rename or move. Falls back to a re-scan when the change is one a flat path list
	 * cannot describe: a directory carrying a subtree with it, a source that was never cached (an
	 * unarchive arrives from outside this index), or a project with duplicate scene ids.
	 */
	fun onMoved(sourcePath: HPath, targetPath: HPath) = synchronized(lock) {
		val current = paths
		val source = sourcePath.toOkioPath()
		if (current == null || hasDuplicateIds || childCountsLocked()[source] != null) {
			invalidateLocked()
			return@synchronized
		}
		val removeAt = current.indexOfPath(source)
		if (removeAt < 0) {
			invalidateLocked()
			return@synchronized
		}

		val target = targetPath.toOkioPath()
		// Archiving moves a scene outside this index's scope, so it leaves rather than moves.
		val targetIsIndexed = validateSceneFilename(targetPath.name) && !target.isInArchivedDirectory()

		// An atomicMove replaces whatever occupied the target, so that path leaves too.
		val remaining = current.withIndexRemoved(removeAt)
		val displacedAt = if (targetIsIndexed) remaining.indexOfPath(target) else -1
		val survivors = if (displacedAt < 0) remaining else remaining.withIndexRemoved(displacedAt)
		paths = if (targetIsIndexed) survivors.withPathInserted(targetPath) else survivors

		// Re-IDing moves a scene to a filename carrying a different id, so retire the old one. If
		// that id is already claimed elsewhere the move has created a duplicate, which name order
		// decides across the whole project, so hand it to the re-scan.
		pathsById?.let { byId ->
			byId.remove(sceneIdOf(current[removeAt]))
			if (targetIsIndexed) {
				val targetId = sceneIdOf(targetPath)
				val claimant = byId[targetId]
				if (claimant != null && claimant.toOkioPath() != target) {
					// The rebuilt index re-detects the duplicate and latches the flag itself.
					invalidateLocked()
					return@synchronized
				}
				byId[targetId] = targetPath
			}
		}
		childCounts?.let { counts ->
			counts.decrement(source.parent)
			// A displaced file leaves as the moved one arrives, so that directory's count holds.
			if (targetIsIndexed && displacedAt < 0) counts.increment(target.parent)
		}
	}

	// region Locked internals

	private fun invalidateLocked() {
		paths = null
		pathsById = null
		childCounts = null
		hasDuplicateIds = false
	}

	private fun pathsLocked(): List<HPath> = paths ?: scan().also { paths = it }

	private fun pathsByIdLocked(): Map<Int, HPath> {
		pathsById?.let { return it }

		val byId = HashMap<Int, HPath>()
		// The scan is name-ordered, so the first path seen for an id is the one that owns it.
		for (path in pathsLocked()) {
			val id = sceneIdOf(path)
			if (byId.containsKey(id)) hasDuplicateIds = true else byId[id] = path
		}
		return byId.also { pathsById = it }
	}

	private fun childCountsLocked(): MutableMap<Path, Int> {
		childCounts?.let { return it }

		val counts = HashMap<Path, Int>()
		for (path in pathsLocked()) counts.increment(path.toOkioPath().parent)
		return counts.also { childCounts = it }
	}

	// endregion

	private fun sceneIdOf(path: HPath): Int =
		SceneDatasource.getSceneIdFromFilename(path.toOkioPath().name)
}

private fun MutableMap<Path, Int>.increment(parent: Path?) {
	if (parent != null) this[parent] = (this[parent] ?: 0) + 1
}

private fun MutableMap<Path, Int>.decrement(parent: Path?) {
	if (parent == null) return
	val next = (this[parent] ?: 0) - 1
	if (next > 0) this[parent] = next else remove(parent)
}

private fun List<HPath>.withIndexRemoved(index: Int): List<HPath> = buildList(size - 1) {
	addAll(this@withIndexRemoved.subList(0, index))
	addAll(this@withIndexRemoved.subList(index + 1, this@withIndexRemoved.size))
}

/** Inserts in name order, so the list never has to be re-sorted. */
private fun List<HPath>.withPathInserted(path: HPath): List<HPath> {
	val at = upperBoundByName(path.name)
	return buildList(size + 1) {
		addAll(this@withPathInserted.subList(0, at))
		add(path)
		addAll(this@withPathInserted.subList(at, this@withPathInserted.size))
	}
}

/**
 * Index of [target], or -1. Names carry the scene id so they are unique in practice, but the
 * equal-name run is scanned rather than assumed to hold a single entry.
 */
private fun List<HPath>.indexOfPath(target: Path): Int {
	val name = target.name
	var index = upperBoundByName(name) - 1
	while (index >= 0 && this[index].name == name) {
		if (this[index].toOkioPath() == target) return index
		index--
	}
	return -1
}

/** Index just past the last path whose name is `<= name`. */
private fun List<HPath>.upperBoundByName(name: String): Int {
	var low = 0
	var high = size
	while (low < high) {
		val mid = (low + high) ushr 1
		if (this[mid].name <= name) low = mid + 1 else high = mid
	}
	return low
}
