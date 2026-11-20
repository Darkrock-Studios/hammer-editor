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
 * editor save on a later local calendar day, or more than [MERGE_GAP] after the last save that
 * recorded words.
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

	private class Anchor(val words: ActivityWords, text: String) {
		var credit = 0
		var lastSeen: String = text
		var lastSeenWords: ActivityWords = words

		/** Ticket of the editor save [credit] and [lastSeen] come from. */
		var ticket = 0L

		/** Anchored at [content] with no editor save since, so re-anchoring there changes nothing. */
		fun isAt(content: String): Boolean = lastSeenWords === words && lastSeen == content
	}

	/** Guards the session log and its cache. */
	private val mutex = Mutex()

	/** Guards everything below it. Held only for map and field updates, never to tokenize or hash. */
	private val baselineLock = reentrantLock()
	private val anchors = mutableMapOf<Int, Anchor>()

	/** Shingles of every text anchored or left behind since the project opened. Only grows. */
	private var reproduced: PersistentSet<Long> = persistentHashSetOf()
	private var banked = 0
	private var highWater = 0
	private var epochStart: Instant? = null
	private var lastRecorded: Instant? = null
	private var nextTicket = 0L

	// Cached in memory once loaded from disk; kept in sync with every save.
	private var sessions: List<WritingSession>? = null

	/** Anchors a scene to [content] unless it already has an anchor. */
	fun ensureBaseline(sceneId: Int, content: String) {
		if (baselineLock.withReentrantLock { sceneId in anchors }) return
		val words = ActivityWords.of(content)
		addSeen(words)
		baselineLock.withReentrantLock {
			if (sceneId !in anchors) install(sceneId, content, words)
		}
	}

	/** Anchors a scene to [content], banking the credit of any anchor it had. */
	fun rememberBaseline(sceneId: Int, content: String) {
		if (baselineLock.withReentrantLock { anchors[sceneId]?.isAt(content) == true }) return
		val words = ActivityWords.of(content)
		addSeen(words)
		val left = baselineLock.withReentrantLock {
			val old = anchors[sceneId]?.also { banked += it.credit }
			install(sceneId, content, words)
			old?.lastSeenWords
		}
		left?.let(::addSeen)
	}

	/** Re-anchors a scene the tracker holds at [content], as [rememberBaseline] does; others stay untracked. */
	fun replaceBaseline(sceneId: Int, content: String) {
		if (baselineLock.withReentrantLock { sceneId in anchors }) rememberBaseline(sceneId, content)
	}

	/** Drops the anchor of a deleted scene, banking its credit. What it held stays seen. */
	fun forgetBaseline(sceneId: Int) {
		val left = baselineLock.withReentrantLock {
			anchors.remove(sceneId)?.also { banked += it.credit }
		}
		left?.let { addSeen(it.lastSeenWords) }
	}

	/** Moves a scene's anchor to its new id. */
	fun reIdBaseline(oldId: Int, newId: Int) {
		val left = baselineLock.withReentrantLock {
			val moved = anchors.remove(oldId) ?: return@withReentrantLock null
			val displaced = anchors.put(newId, moved)?.also { banked += it.credit }
			displaced?.lastSeenWords
		}
		left?.let(::addSeen)
	}

	/**
	 * Runs [block], which reads and rewrites this device's own log, with no write recorded in between.
	 * The next write reloads the log from disk. [block] must not call back into the tracker.
	 */
	suspend fun <T> updateOwnLog(block: suspend () -> T): T = mutex.withLock {
		try {
			block()
		} finally {
			sessions = null
		}
	}

	/**
	 * Processes a successful scene save and returns the words recorded for it. An editor save is
	 * credited against the scene's anchor, or anchors a scene that has none and credits nothing. Any
	 * other source re-anchors a scene the tracker holds. Call off the main thread: an editor save
	 * diffs the scene against its anchor.
	 */
	suspend fun onSceneSaved(sceneId: Int, newContent: String, source: UpdateSource): Int {
		if (source != UpdateSource.Editor) {
			replaceBaseline(sceneId, newContent)
			return 0
		}

		val words = ActivityWords.of(newContent)
		val (recorded, at) = creditEditorSave(sceneId, newContent, words)
		if (recorded > 0) recordWriting(recorded, at)
		return recorded
	}

	private fun creditEditorSave(sceneId: Int, content: String, words: ActivityWords): Pair<Int, Instant> {
		val ticket = baselineLock.withReentrantLock { ++nextTicket }
		while (true) {
			val snapshot = baselineLock.withReentrantLock {
				val now = clock.now()
				val restarted = restartEpochIfDue(now)
				EditorSaveSnapshot(now, anchors[sceneId], reproduced, restarted)
			}
			if (snapshot.restarted.isNotEmpty()) {
				snapshot.restarted.forEach(::addSeen)
				continue
			}
			val anchor = snapshot.anchor
			if (anchor == null) {
				addSeen(words)
				baselineLock.withReentrantLock { if (sceneId !in anchors) install(sceneId, content, words) }
				return 0 to snapshot.now
			}

			val credit = countOriginalWords(anchor.words, words, snapshot.seen)

			val recorded = baselineLock.withReentrantLock {
				when {
					anchors[sceneId] !== anchor -> null
					ticket < anchor.ticket -> 0
					else -> {
						anchor.credit = credit
						anchor.lastSeen = content
						anchor.lastSeenWords = words
						anchor.ticket = ticket
						val total = banked + anchors.values.sumOf { it.credit }
						val growth = (total - highWater).coerceAtLeast(0)
						highWater = maxOf(highWater, total)
						if (growth > 0) lastRecorded = snapshot.now
						growth
					}
				}
			}
			if (recorded != null) return recorded to snapshot.now
		}
	}

	private class EditorSaveSnapshot(
		val now: Instant,
		val anchor: Anchor?,
		val seen: Set<Long>,
		val restarted: List<ActivityWords>,
	)

	/**
	 * Under [baselineLock]. Starts a new epoch when one is due, re-anchoring every scene at its last
	 * seen text, and returns those texts for [addSeen].
	 */
	private fun restartEpochIfDue(now: Instant): List<ActivityWords> {
		val start = epochStart
		if (start == null) {
			epochStart = now
			return emptyList()
		}
		val timeZone = TimeZone.currentSystemDefault()
		val laterDay = now.toLocalDateTime(timeZone).date > start.toLocalDateTime(timeZone).date
		val gap = lastRecorded?.let { now - it > MERGE_GAP } == true
		if (!laterDay && !gap) return emptyList()

		val texts = anchors.values.map { it.lastSeenWords }
		for (entry in anchors.entries) entry.setValue(Anchor(entry.value.lastSeenWords, entry.value.lastSeen))
		banked = 0
		highWater = 0
		epochStart = now
		lastRecorded = null
		return texts
	}

	/** Under [baselineLock]. */
	private fun install(sceneId: Int, content: String, words: ActivityWords) {
		if (epochStart == null) epochStart = clock.now()
		anchors[sceneId] = Anchor(words, content)
	}

	/** Adds the shingles of [words] to [reproduced] without hashing under [baselineLock]. */
	private fun addSeen(words: ActivityWords) {
		val shingles = words.shingles().toList()
		if (shingles.isEmpty()) return
		while (true) {
			val base = baselineLock.withReentrantLock { reproduced }
			val merged = base.addAll(shingles)
			val swapped = baselineLock.withReentrantLock {
				(reproduced === base).also { if (it) reproduced = merged }
			}
			if (swapped) return
		}
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
