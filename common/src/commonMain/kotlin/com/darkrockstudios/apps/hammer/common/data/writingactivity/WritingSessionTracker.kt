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
 *
 * Anchoring only records text; it is tokenized and its shingles added by the next editor save, on
 * that save's thread.
 */
class WritingSessionTracker(
	private val repository: WritingActivityRepository,
	private val clock: Clock,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private class Anchor(val text: String, val words: Lazy<ActivityWords>) {
		var credit = 0
		var lastSeen: String = text
		var lastSeenWords: Lazy<ActivityWords> = words

		/** Ticket of the editor save [credit] and [lastSeen] come from. */
		var ticket = 0L

		/** Anchored at [content] with no editor save since, so re-anchoring there changes nothing. */
		fun isAt(content: String): Boolean = lastSeenWords === words && text == content
	}

	/** Guards the session log and its cache. */
	private val mutex = Mutex()

	/** Guards everything below it. Held only for map and field updates, never to tokenize or hash. */
	private val baselineLock = reentrantLock()
	private val anchors = mutableMapOf<Int, Anchor>()

	/** Shingles of every text anchored or left behind since the project opened. Only grows. */
	private var reproduced: PersistentSet<Long> = persistentHashSetOf()

	/** Texts whose shingles belong in [reproduced] but are not added yet. */
	private val unseen = mutableListOf<Lazy<ActivityWords>>()
	private var banked = 0
	private var highWater = 0
	private var epochStart: Instant? = null
	private var lastRecorded: Instant? = null
	private var nextTicket = 0L

	// Cached in memory once loaded from disk; kept in sync with every save.
	private var sessions: List<WritingSession>? = null
	private var sessionsUnsaved = false

	/** Anchors a scene to [content] unless it already has an anchor. */
	fun ensureBaseline(sceneId: Int, content: String) {
		baselineLock.withReentrantLock {
			if (sceneId !in anchors) install(sceneId, content, lazy { ActivityWords.of(content) })
		}
	}

	/** Anchors a scene to [content], banking the credit of any anchor it had. */
	fun rememberBaseline(sceneId: Int, content: String) {
		baselineLock.withReentrantLock { reanchor(sceneId, content) }
	}

	/** Re-anchors a scene the tracker holds at [content], as [rememberBaseline] does; others stay untracked. */
	fun replaceBaseline(sceneId: Int, content: String) {
		baselineLock.withReentrantLock {
			if (sceneId in anchors) reanchor(sceneId, content)
		}
	}

	/** Drops the anchor of a deleted scene, banking its credit. What it held stays seen. */
	fun forgetBaseline(sceneId: Int) {
		baselineLock.withReentrantLock {
			anchors.remove(sceneId)?.let(::retire)
		}
	}

	/** Moves a scene's anchor to its new id. */
	fun reIdBaseline(oldId: Int, newId: Int) {
		baselineLock.withReentrantLock {
			val moved = anchors.remove(oldId) ?: return@withReentrantLock
			anchors.put(newId, moved)?.let(::retire)
		}
	}

	/**
	 * Runs [block], which reads and rewrites this device's own log, with no write recorded in between.
	 * The next write reloads the log from disk. [block] must not call back into the tracker.
	 */
	suspend fun <T> updateOwnLog(block: suspend () -> T): T = mutex.withLock {
		sessions?.takeIf { sessionsUnsaved }?.let { persist(it) }
		try {
			block()
		} finally {
			sessions = null
			sessionsUnsaved = false
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
			markSeen()
			val snapshot = baselineLock.withReentrantLock {
				val now = clock.now()
				restartEpochIfDue(now)
				if (unseen.isEmpty()) EditorSaveSnapshot(now, anchors[sceneId], reproduced) else null
			} ?: continue
			val anchor = snapshot.anchor
			if (anchor == null) {
				baselineLock.withReentrantLock {
					if (sceneId !in anchors) install(sceneId, content, lazyOf(words))
				}
				return 0 to snapshot.now
			}

			val credit = countOriginalWords(anchor.words.value, words, snapshot.seen)

			val recorded = baselineLock.withReentrantLock {
				when {
					anchors[sceneId] !== anchor -> null
					ticket < anchor.ticket -> 0
					else -> {
						anchor.credit = credit
						anchor.lastSeen = content
						anchor.lastSeenWords = lazyOf(words)
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

	private class EditorSaveSnapshot(val now: Instant, val anchor: Anchor?, val seen: Set<Long>)

	/** Under [baselineLock]. Starts a new epoch when one is due, re-anchoring every scene at its last seen text. */
	private fun restartEpochIfDue(now: Instant) {
		val start = epochStart
		if (start == null) {
			epochStart = now
			return
		}
		val timeZone = TimeZone.currentSystemDefault()
		val laterDay = now.toLocalDateTime(timeZone).date > start.toLocalDateTime(timeZone).date
		val gap = lastRecorded?.let { now - it > MERGE_GAP } == true
		if (!laterDay && !gap) return

		for (entry in anchors.entries) {
			val anchor = entry.value
			entry.setValue(Anchor(anchor.lastSeen, anchor.lastSeenWords))
			unseen += anchor.lastSeenWords
		}
		banked = 0
		highWater = 0
		epochStart = now
		lastRecorded = null
	}

	/** Under [baselineLock]. */
	private fun reanchor(sceneId: Int, content: String) {
		val old = anchors[sceneId]
		if (old?.isAt(content) == true) return
		old?.let(::retire)
		install(sceneId, content, lazy { ActivityWords.of(content) })
	}

	/** Under [baselineLock]: banks a dropped anchor's credit and keeps its text seen. */
	private fun retire(anchor: Anchor) {
		banked += anchor.credit
		unseen += anchor.lastSeenWords
	}

	/** Under [baselineLock]. */
	private fun install(sceneId: Int, content: String, words: Lazy<ActivityWords>) {
		if (epochStart == null) epochStart = clock.now()
		anchors[sceneId] = Anchor(content, words)
		unseen += words
	}

	/** Moves [unseen] texts into [reproduced], tokenizing and hashing outside [baselineLock]. */
	private fun markSeen() {
		val pending = baselineLock.withReentrantLock { unseen.toList() }
		if (pending.isEmpty()) return
		val shingles = pending.flatMap { it.value.shingles() }
		while (true) {
			val base = baselineLock.withReentrantLock { reproduced }
			val merged = base.addAll(shingles)
			val swapped = baselineLock.withReentrantLock {
				(reproduced === base).also { swapped ->
					if (swapped) {
						reproduced = merged
						unseen.removeAll { added -> pending.any { it === added } }
					}
				}
			}
			if (swapped) return
		}
	}

	private suspend fun recordWriting(words: Int, at: Instant) = mutex.withLock {
		val current = sessions ?: repository.loadOwnLog().sessions
		val updated = mergeWriting(current, words, at, TimeZone.currentSystemDefault())
		sessions = updated
		persist(updated)
	}

	/** Under [mutex]. A failed write keeps the sessions in memory for the next one to retry. */
	@Suppress("TooGenericExceptionCaught") // Persist failure keeps in-memory state for retry
	private suspend fun persist(updated: List<WritingSession>) {
		sessionsUnsaved = try {
			repository.saveOwnLog(updated)
			false
		} catch (e: Exception) {
			Napier.e("Failed to persist writing activity", e)
			true
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
