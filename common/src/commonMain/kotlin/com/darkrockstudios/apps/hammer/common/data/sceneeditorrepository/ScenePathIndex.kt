package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.Path

/**
 * A cached view of the scene files on disk, backed by one recursive [scan]. Everything a caller can
 * ask is derived from that scan: the name-ordered path list, the path owning a scene id, and how
 * many scenes sit directly inside a directory.
 *
 * Invariants, all maintained under one lock:
 * - Paths are ordered by file name, matching what the scan produces.
 * - A scene id maps to the name-first path claiming it, which is how duplicate ids are settled.
 * - A directory's count is the number of cached paths whose parent it is.
 *
 * A new file is absorbed in place, since it cannot change any of those answers for a file already
 * held. Every other change drops the cache and the next read re-scans, so callers that rename in a
 * loop should read what they need before it, not inside it.
 */
internal class ScenePathIndex(private val scan: () -> List<HPath>) {

	private val lock = SynchronizedObject()

	private var paths: List<HPath>? = null
	private var pathsById: MutableMap<Int, HPath>? = null
	private var childCounts: MutableMap<Path, Int>? = null

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
			if (owner == null || scenePath.name < owner.name) byId[id] = scenePath
		}
		childCounts?.let { counts -> counts.increment(scenePath.toOkioPath().parent) }
	}

	// region Locked internals

	private fun invalidateLocked() {
		paths = null
		pathsById = null
		childCounts = null
	}

	private fun pathsLocked(): List<HPath> = paths ?: scan().also { paths = it }

	private fun pathsByIdLocked(): Map<Int, HPath> {
		pathsById?.let { return it }

		val byId = HashMap<Int, HPath>()
		// The scan is name-ordered, so the first path seen for an id is the one that owns it.
		for (path in pathsLocked()) {
			val id = sceneIdOf(path)
			if (!byId.containsKey(id)) byId[id] = path
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

/** Inserts in name order, so the list never has to be re-sorted. */
private fun List<HPath>.withPathInserted(path: HPath): List<HPath> {
	val at = upperBoundByName(path.name)
	return buildList(size + 1) {
		addAll(this@withPathInserted.subList(0, at))
		add(path)
		addAll(this@withPathInserted.subList(at, this@withPathInserted.size))
	}
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
