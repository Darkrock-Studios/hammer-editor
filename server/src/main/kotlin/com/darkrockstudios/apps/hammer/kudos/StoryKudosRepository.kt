package com.darkrockstudios.apps.hammer.kudos

import com.darkrockstudios.apps.hammer.database.StoryKudosDao

sealed interface SetPicksResult {
	data class Saved(val picks: Set<KudosKind>) : SetPicksResult
	data object OwnStory : SetPicksResult
	data object Disabled : SetPicksResult
	data object OverCap : SetPicksResult
}

data class KudosTally(
	val counts: Map<KudosKind, Long>,
	val givers: Long,
) {
	/** Kinds with at least one pick, most-picked first, ties in display order. */
	fun ranked(group: KudosGroup): List<Pair<KudosKind, Long>> =
		counts.filterKeys { it.group == group }
			.filterValues { it > 0 }
			.toList()
			.sortedWith(compareByDescending<Pair<KudosKind, Long>> { it.second }.thenBy { it.first.ordinal })

	/** What the public page may show: names only, and only once enough readers agree. */
	fun publicHighlights(group: KudosGroup): List<KudosKind> =
		ranked(group).filter { it.second >= PUBLIC_THRESHOLD }.map { it.first }

	companion object {
		const val PUBLIC_THRESHOLD = 3L
	}
}

class StoryKudosRepository(
	private val storyKudosDao: StoryKudosDao,
) {

	suspend fun picksFor(projectId: Long, userId: Long): Set<KudosKind> =
		storyKudosDao.kindsForUser(projectId, userId).mapNotNullTo(mutableSetOf(), KudosKind::fromKey)

	/**
	 * Replaces the giver's picks. Picking a second reaction swaps it in for the previous one,
	 * so a whole-form submit with both checked keeps only the newly picked reaction.
	 */
	suspend fun setPicks(
		projectId: Long,
		authorId: Long,
		giverId: Long,
		requested: Set<KudosKind>,
	): SetPicksResult {
		if (giverId == authorId) return SetPicksResult.OwnStory
		if (!isEnabled(projectId)) return SetPicksResult.Disabled

		val previous = picksFor(projectId, giverId)
		val picks = swapReaction(requested, previous)
		val overCap = KudosGroup.entries.any { group -> picks.count { it.group == group } > group.maxPicks }
		if (overCap) return SetPicksResult.OverCap

		storyKudosDao.replacePicks(projectId, giverId, picks.mapTo(mutableSetOf()) { it.key })
		return SetPicksResult.Saved(picks)
	}

	suspend fun tally(projectId: Long): KudosTally {
		val keys = KudosKind.entries.map { it.key }
		val counts = storyKudosDao.countsForProject(projectId, keys)
			.mapNotNull { (key, count) -> KudosKind.fromKey(key)?.let { it to count } }
			.toMap()
		return KudosTally(counts = counts, givers = storyKudosDao.giverCountForProject(projectId, keys))
	}

	suspend fun isEnabled(projectId: Long): Boolean = !storyKudosDao.isOptedOut(projectId)

	suspend fun setEnabled(projectId: Long, enabled: Boolean) =
		storyKudosDao.setOptedOut(projectId, optedOut = !enabled)

	private fun swapReaction(requested: Set<KudosKind>, previous: Set<KudosKind>): Set<KudosKind> {
		val reactions = requested.filter { it.group == KudosGroup.REACTION }.toSet()
		if (reactions.size <= 1) return requested
		val newlyPicked = reactions - previous
		return if (newlyPicked.size == 1) requested - (reactions - newlyPicked) else requested
	}
}
