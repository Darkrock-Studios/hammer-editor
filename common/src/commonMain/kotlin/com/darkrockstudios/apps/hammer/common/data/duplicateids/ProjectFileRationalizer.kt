package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

/**
 * Finds project files that match a store's filename pattern but share an id with another file,
 * and reduces each such set to one file per id, so every read and write path agrees on which
 * file owns an id.
 *
 * Resolving always leaves the kept content at the canonical filename and deletes the rest. When
 * two devices syncing through a file-sync tool resolve the same set differently, that converges on
 * one file instead of each side deleting the copy the other kept.
 */
class ProjectFileRationalizer(
	private val projectDef: ProjectDef,
	private val fileSystem: FileSystem,
	private val toml: Toml,
) {
	// Entries before images: resolving an entry can move or delete images.
	private val handlers: Map<DuplicateIdKind, DuplicateIdHandler> = listOf(
		EntryDuplicateIdHandler(projectDef, fileSystem, toml),
		EntryImageDuplicateIdHandler(projectDef, fileSystem),
		SceneDuplicateIdHandler(projectDef, fileSystem),
		DraftDuplicateIdHandler(projectDef, fileSystem),
		NoteDuplicateIdHandler(projectDef, fileSystem, toml),
	).associateBy { it.kind }

	fun findDuplicates(): List<DuplicateIdSet> = handlers.values.flatMap { it.findDuplicates() }

	fun hasDuplicates(): Boolean = handlers.values.any { it.findDuplicates().isNotEmpty() }

	/**
	 * Ids held by items of more than one type, with the types holding each. Ids are one space
	 * across types, since sync keys every entity by id alone.
	 */
	fun findCrossTypeIds(): Map<Int, List<EntityType>> {
		val holders = mutableMapOf<Int, MutableList<EntityType>>()
		for (handler in handlers.values) {
			val type = handler.entityType ?: continue
			handler.ids().forEach { id -> holders.getOrPut(id) { mutableListOf() } += type }
		}
		timelineIds().forEach { id -> holders.getOrPut(id) { mutableListOf() } += EntityType.TimelineEvent }
		return holders.filterValues { it.size > 1 }
	}

	// Timeline events share one file, so they can't be duplicated on disk, but their ids can collide.
	private fun timelineIds(): Set<Int> =
		TimeLineDatasource.loadTimeline(TimeLineDatasource.getTimelineFilePath(projectDef), fileSystem, toml)
			.events.mapTo(mutableSetOf()) { it.id }

	/** Every copy has the same bytes (or is a group) and would end up at the same path. */
	fun isSafeToResolve(set: DuplicateIdSet): Boolean {
		val handler = handlers.getValue(set.kind)
		val sameContent = set.copies.all { it.isGroup } ||
			set.copies.map { it.contentHash }.let { hashes -> null !in hashes && hashes.distinct().size == 1 }
		return sameContent && set.copies.map { handler.canonicalPath(it, set.id) }.distinct().size == 1
	}

	/**
	 * Resolves every set [isSafeToResolve] allows, keeping the copy in use. One set at a time with
	 * a fresh scan between, since resolving one (merging groups) can move the files of another.
	 */
	fun resolveSafeDuplicates(): Resolution {
		val markDirty = mutableSetOf<Int>()
		val remaining = mutableListOf<DuplicateIdSet>()
		for (handler in handlers.values) {
			var passes = 0
			while (true) {
				val sets = handler.findDuplicates()
				val safe = sets.firstOrNull { isSafeToResolve(it) }
				if (safe == null || passes++ >= MAX_SAFE_PASSES) {
					remaining += sets
					break
				}
				Napier.i("Resolving identical copies of ${safe.kind} ${safe.id}: ${safe.copies.map { it.relativePath }}")
				markDirty += apply(safe, owner = safe.inUse, newIds = emptyMap())
			}
		}
		return Resolution(remaining, markDirty)
	}

	/**
	 * Keeps the copies at [keep] and deletes the rest. The first kept copy in read order keeps the
	 * id; every other kept copy gets an id from [claimId].
	 *
	 * @return ids whose files changed in a way the sync server hasn't seen.
	 * @throws StaleDuplicateIdSet if the files changed since [set] was scanned.
	 */
	suspend fun resolve(set: DuplicateIdSet, keep: Set<HPath>, claimId: suspend () -> Int): Set<Int> {
		val keptCopies = set.copies.filter { it.path in keep }
		require(keptCopies.isNotEmpty()) { "At least one copy must be kept" }
		require(keptCopies.size == keep.size) { "Kept paths must be copies in the set" }
		require(set.canKeepSeveral || keptCopies.size == 1) { "Only one ${set.kind} copy can be kept" }

		if (set !in handlers.getValue(set.kind).findDuplicates()) throw StaleDuplicateIdSet(set)

		val owner = keptCopies.first()
		val newIds = keptCopies.drop(1).associate { it.path to claimId() }
		Napier.i("Resolving ${set.kind} ${set.id}: keeping ${owner.relativePath}, new ids $newIds")
		return apply(set, owner, newIds)
	}

	private fun apply(set: DuplicateIdSet, owner: DuplicateCopy, newIds: Map<HPath, Int>): Set<Int> {
		val handler = handlers.getValue(set.kind)
		val markDirty = mutableSetOf<Int>()
		val moved = mutableMapOf<HPath, HPath>()
		fun DuplicateCopy.current() = copy(path = path.remapped(moved))

		for (discarded in set.copies.filter { it != owner && it.path !in newIds }) {
			val result = handler.discard(discarded.current(), owner.current())
			markDirty += result.markDirty
			moved += result.moved
		}
		for ((path, newId) in newIds) {
			val kept = set.copies.first { it.path == path }
			markDirty += handler.claim(kept.current(), owner.current(), set.id, newId)
		}
		markDirty += handler.claim(owner.current(), owner.current(), set.id, set.id)

		if (owner != set.inUse) markDirty += set.id
		return markDirty
	}

	data class Resolution(
		val remaining: List<DuplicateIdSet>,
		val markDirty: Set<Int>,
	)

	private companion object {
		const val MAX_SAFE_PASSES = 100
	}
}

/** Follows this path through [moved] directories, so a path inside a moved group still resolves. */
private fun HPath.remapped(moved: Map<HPath, HPath>): HPath {
	val path = toOkioPath()
	for ((from, to) in moved) {
		val fromPath = from.toOkioPath()
		if (path == fromPath) return to
		if (path.isWithin(fromPath)) return (to.toOkioPath() / path.relativeTo(fromPath)).toHPath()
	}
	return this
}
