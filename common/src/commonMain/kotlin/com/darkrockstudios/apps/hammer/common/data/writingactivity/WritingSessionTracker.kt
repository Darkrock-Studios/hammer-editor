package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Captures writing activity from scene saves and keeps the on-disk
 * `writing_activity/{deviceId}.toml` log up to date for the active project.
 *
 * Two responsibilities:
 *  1. Remember each open scene's pre-edit text (the "baseline") so we can
 *     compute how many words were *added* on save, ignoring deletions and
 *     pure rearrangements.
 *  2. Roll those word-add events up into [WritingSession] entries with the
 *     "extend in place / seal on calendar-day rollover or long gap" rules.
 */
class WritingSessionTracker(
	private val repository: WritingActivityRepository,
	private val clock: Clock,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val mutex = Mutex()
	private val baseline = mutableMapOf<Int, String>()

	// Cached in memory once loaded from disk; kept in sync with every save.
	private var sessions: List<WritingSession>? = null

	/** Records the pre-edit content of a scene the user just opened. */
	fun rememberBaseline(sceneId: Int, content: String) {
		baseline[sceneId] = content
	}

	/** Drops the baseline for a scene that's been deleted. */
	fun forgetBaseline(sceneId: Int) {
		baseline.remove(sceneId)
	}

	/**
	 * Forces the next [onSceneSaved] call to reload the session list from
	 * disk. Call this after sync writes a merged log so the tracker doesn't
	 * keep extending a stale in-memory copy.
	 */
	suspend fun invalidateSessionCache() = mutex.withLock {
		sessions = null
	}

	/**
	 * Process a successful scene save. Returns the number of words credited
	 * to this save (0 for non-Editor sources, no baseline yet, or no net
	 * additions). When the count is positive, the on-disk session log is
	 * updated.
	 */
	suspend fun onSceneSaved(sceneId: Int, newContent: String, source: UpdateSource): Int {
		if (source != UpdateSource.Editor) return 0

		val oldContent = baseline[sceneId]
		if (oldContent == null) {
			// We never saw the pre-edit text (e.g. scene loaded before the
			// tracker was wired up, or buffer was populated from sync). Don't
			// credit anything — establish a baseline going forward.
			baseline[sceneId] = newContent
			return 0
		}

		val added = countAddedWords(oldContent, newContent)
		baseline[sceneId] = newContent

		if (added > 0) {
			recordWriting(added, clock.now())
		}
		return added
	}

	private suspend fun recordWriting(words: Int, at: Instant) = mutex.withLock {
		val current = sessions ?: repository.loadOwnLog().sessions
		val updated = mergeWriting(current, words, at, TimeZone.currentSystemDefault())
		sessions = updated
		try {
			repository.saveOwnLog(updated)
		} catch (e: Exception) {
			Napier.e("Failed to persist writing activity", e)
			// Keep the in-memory state — we'll retry on the next delta.
		}
	}

	companion object {
		val MERGE_GAP: Duration = 6.hours

		/**
		 * Pure lifecycle math. Given the current session list and a new
		 * write event, decide whether to extend the last session, seal it
		 * and open a new one, or just append.
		 */
		fun mergeWriting(
			current: List<WritingSession>,
			words: Int,
			at: Instant,
			tz: TimeZone,
		): List<WritingSession> {
			val last = current.lastOrNull()
			val newSession = WritingSession(startedAt = at, endedAt = at, wordsWritten = words)

			if (last == null || last.sealed) {
				return current + newSession
			}

			val sameCalendarDay = last.startedAt.toLocalDateTime(tz).date ==
				at.toLocalDateTime(tz).date
			val gapTooLarge = (at - last.endedAt) > MERGE_GAP

			return if (!sameCalendarDay || gapTooLarge) {
				current.dropLast(1) + last.copy(sealed = true) + newSession
			} else {
				current.dropLast(1) + last.copy(
					endedAt = at,
					wordsWritten = last.wordsWritten + words,
				)
			}
		}
	}
}
