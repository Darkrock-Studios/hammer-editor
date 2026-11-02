package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/**
 * Keeps timeline placements and references pointing at a scene or entry when sync re-ids it.
 * Never throws: a failed rewrite is logged and reports nothing.
 */
class TimelineReferenceRemapper(private val timeLineRepository: TimeLineRepository) {
	/** Returns the ids of the timeline events that were rewritten. */
	@Suppress("TooGenericExceptionCaught")
	suspend fun remap(oldId: Int, newId: Int, subjectKind: TimelineKind): Set<Int> =
		try {
			timeLineRepository.remapReferencedId(oldId, newId, subjectKind)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Napier.e("Timeline reference remap failed for $oldId -> $newId", e)
			emptySet()
		}
}
