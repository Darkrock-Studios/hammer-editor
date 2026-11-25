package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsUseCase
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import io.github.aakira.napier.Napier
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps a project's cached statistics true across a sync. An edit on this device marks them stale
 * through the editor, but what a sync brings in is written straight to disk, so a device went on
 * showing its pre-sync numbers (two devices holding the same text showed 97 and 113 words).
 *
 * The sync's steps report what they changed, each marking the cache stale at once; at the end,
 * [refreshIfStale] recounts once, so the project list shows the synced numbers without the project
 * being opened.
 */
class SyncStatisticsRefresher(
	private val statisticsRepository: StatisticsRepository,
	private val referenceIndexRepository: ReferenceIndexRepository,
	private val projectStatisticsUseCase: ProjectStatisticsUseCase,
) {
	/** The sync stored, replaced or removed a scene or encyclopedia entry. */
	suspend fun contentChanged() {
		referenceIndexRepository.markDirtyNow()
		statisticsRepository.markDirtyNow()
	}

	/** The sync changed writing activity, this device's or another's. */
	suspend fun activityChanged() {
		statisticsRepository.markDirtyNow()
	}

	/** Recounts if anything marked the statistics stale. If that fails they stay stale for next time. */
	suspend fun refreshIfStale() {
		if (!statisticsRepository.isDirty.value) return
		try {
			projectStatisticsUseCase.load()
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w("Statistics could not be recounted after sync; they stay marked stale", e)
		}
	}
}
