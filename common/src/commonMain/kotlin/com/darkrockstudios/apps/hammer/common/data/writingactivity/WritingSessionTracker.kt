package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock as withReentrantLock
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Captures writing activity from scene saves and keeps this device's activity log up to date for
 * the active project.
 *
 * Each scene is anchored to its text when the tracker first sees it in an epoch, and an editor save
 * is credited with the words typed since that anchor ([countOriginalWords]) that are not text the
 * tracker has seen before. Editor saves never move the anchor, so autosaves landing mid-word, typos
 * fixed later and undone deletions credit nothing extra. Text replaced from outside the editor
 * re-anchors the scene, banking its credit. An epoch ends where a [WritingSession] is sealed: on an
 * editor save on a new local calendar day, or more than [MERGE_GAP] after the previous one.
 *
 * Saves record the growth of the epoch's total (banked plus every scene's credit) past its highest
 * point so far, so deleting never subtracts and retyping deleted words never adds.
 */
class WritingSessionTracker(
	private val repository: WritingActivityRepository,
	private val clock: Clock,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private class Anchor(val text: String, val words: ActivityWords) {
		var credit = 0
		var lastSeen: String = text
		var lastSeenWords: ActivityWords = words

		/** Anchored at [content] with nothing typed since, so re-anchoring there changes nothing. */
		fun isAt(content: String): Boolean = text == content && lastSeen == content
	}

	/** Guards the session log and its cache. */
	private val mutex = Mutex()

	/** Guards everything below it. Never held across a suspension or while counting. */
	private val baselineLock = reentrantLock()
	private val anchors = mutableMapOf<Int, Anchor>()

	/** Shingles of every text anchored or left behind since the project opened. Only grows. */
	private var reproduced: PersistentSet<Long> = persistentHashSetOf()
	private var banked = 0
	private var highWater = 0
	private var epochStart: Instant? = null
	private var lastEditorSave: Instant? = null

	// Cached in memory once loaded from disk; kept in sync with every save.
	private var sessions: List<WritingSession>? = null

	/** Anchors a scene to [content] unless it already has an anchor. */
	fun ensureBaseline(sceneId: Int, content: String) {
		if (baselineLock.withReentrantLock { sceneId in anchors }) return
		val words = ActivityWords.of(content)
		baselineLock.withReentrantLock {
			if (sceneId !in anchors) install(sceneId, content, words)
		}
	}

	/** Anchors a scene to [content], banking the credit of any anchor it had. */
	fun rememberBaseline(sceneId: Int, content: String) {
		if (baselineLock.withReentrantLock { anchors[sceneId]?.isAt(content) == true }) return
		val words = ActivityWords.of(content)
		baselineLock.withReentrantLock { reanchor(sceneId, content, words) }
	}

	/** Drops the anchor of a deleted scene, banking its credit. What it held stays seen. */
	fun forgetBaseline(sceneId: Int) {
		baselineLock.withReentrantLock {
			anchors.remove(sceneId)?.let { anchor ->
				banked += anchor.credit
				addShingles(anchor.lastSeenWords)
			}
		}
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
	 * Processes a successful scene save and returns the words recorded for it. An editor save is
	 * credited against the scene's anchor, or anchors a scene that has none and credits nothing. Any
	 * other source re-anchors the scene to [newContent]. Call off the main thread: an editor save
	 * diffs the scene against its anchor.
	 */
	suspend fun onSceneSaved(sceneId: Int, newContent: String, source: UpdateSource): Int {
		if (source != UpdateSource.Editor) {
			rememberBaseline(sceneId, newContent)
			return 0
		}

		val words = ActivityWords.of(newContent)
		val now = clock.now()
		val recorded = creditEditorSave(sceneId, newContent, words, now)
		if (recorded > 0) recordWriting(recorded, now)
		return recorded
	}

	private fun creditEditorSave(sceneId: Int, content: String, words: ActivityWords, now: Instant): Int {
		while (true) {
			val (anchor, seen) = baselineLock.withReentrantLock {
				restartEpochIfDue(now)
				val anchor = anchors[sceneId]
				if (anchor == null) {
					install(sceneId, content, words)
					lastEditorSave = now
				}
				anchor to reproduced
			}
			if (anchor == null) return 0

			val credit = countOriginalWords(anchor.words, words, seen)

			val recorded = baselineLock.withReentrantLock {
				if (anchors[sceneId] !== anchor) return@withReentrantLock null
				anchor.credit = credit
				anchor.lastSeen = content
				anchor.lastSeenWords = words
				lastEditorSave = now
				val total = banked + anchors.values.sumOf { it.credit }
				(total - highWater).coerceAtLeast(0).also { highWater = maxOf(highWater, total) }
			}
			if (recorded != null) return recorded
		}
	}

	/** Under [baselineLock]. */
	private fun restartEpochIfDue(now: Instant) {
		val start = epochStart
		if (start == null) {
			epochStart = now
			return
		}
		val timeZone = TimeZone.currentSystemDefault()
		val newDay = start.toLocalDateTime(timeZone).date != now.toLocalDateTime(timeZone).date
		val gap = lastEditorSave?.let { now - it > MERGE_GAP } == true
		if (!newDay && !gap) return

		for ((sceneId, anchor) in anchors.entries.toList()) {
			anchors[sceneId] = Anchor(anchor.lastSeen, anchor.lastSeenWords)
			addShingles(anchor.lastSeenWords)
		}
		banked = 0
		highWater = 0
		epochStart = now
	}

	/** Under [baselineLock]. */
	private fun reanchor(sceneId: Int, content: String, words: ActivityWords) {
		anchors[sceneId]?.let { old ->
			banked += old.credit
			addShingles(old.lastSeenWords)
		}
		install(sceneId, content, words)
	}

	/** Under [baselineLock]. */
	private fun install(sceneId: Int, content: String, words: ActivityWords) {
		if (epochStart == null) epochStart = clock.now()
		anchors[sceneId] = Anchor(content, words)
		addShingles(words)
	}

	/** Under [baselineLock]. */
	private fun addShingles(words: ActivityWords) {
		reproduced = reproduced.addAll(words.shingles().toList())
	}

	@Suppress("TooGenericExceptionCaught") // Persist failure keeps in-memory state for retry
	private suspend fun recordWriting(words: Int, at: Instant) = mutex.withLock {
		val current = sessions ?: repository.loadOwnLog().sessions
		val updated = mergeWriting(current, words, at, TimeZone.currentSystemDefault())
		sessions = updated
		try {
			repository.saveOwnLog(updated)
		} catch (e: Exception) {
			Napier.e("Failed to persist writing activity", e)
			// Keep the in-memory state: the next delta retries the write.
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
