package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.wear.sync.CaptureSyncScheduler
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

sealed interface CaptureTarget {
	data class Note(val projectDef: ProjectDef) : CaptureTarget
	data object Idea : CaptureTarget
}

sealed interface CaptureResult {
	/** [pending] is everything on the watch still waiting to reach the server, not just this one. */
	data class Saved(val pending: Int) : CaptureResult
	data object Empty : CaptureResult
	data object Failed : CaptureResult
}

/**
 * Writes a capture. Behind an interface because saving a note needs a full project scope, which a
 * JVM test cannot open.
 */
interface CaptureWriter {
	suspend fun writeNote(projectDef: ProjectDef, text: String): Boolean
	suspend fun writeIdea(text: String): Boolean
}

class CaptureUseCase(
	private val writer: CaptureWriter,
	private val unsyncedContent: UnsyncedContentUseCase,
	private val captureSync: CaptureSyncScheduler,
) {
	suspend fun capture(target: CaptureTarget, text: String): CaptureResult {
		val trimmed = text.trim()
		if (trimmed.isEmpty()) return CaptureResult.Empty

		val written = try {
			when (target) {
				is CaptureTarget.Note -> writer.writeNote(target.projectDef, trimmed)
				CaptureTarget.Idea -> writer.writeIdea(trimmed)
			}
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to save a capture", e)
			false
		}

		if (!written) return CaptureResult.Failed

		captureSync.syncSoon()
		return CaptureResult.Saved(pending = countPending())
	}

	/** The count is only ever shown, so a failure to read it must not fail the capture. */
	private suspend fun countPending(): Int = try {
		unsyncedContent.pending().total
	} catch (e: CancellationException) {
		throw e
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		Napier.e("Failed to count what is waiting to sync", e)
		0
	}
}
