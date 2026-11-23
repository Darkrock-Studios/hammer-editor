package com.darkrockstudios.apps.hammer.common.data.nux

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException
import org.koin.core.component.KoinComponent
import kotlin.random.Random

/**
 * Which hints the user has seen, and how often a screen offers the rest. Tips must never break the
 * app: an unreadable file reads as a fresh install, and a failed save is logged while the change
 * still holds for this session. Hints for a platform other than [platform] never show here.
 */
class NuxRepository(
	private val datasource: NuxDatasource,
	private val globalSettingsStore: GlobalSettingsStore,
	private val platform: NuxPlatform = currentNuxPlatform,
	private val random: Random = Random.Default,
) : KoinComponent {
	private val dispatcherIo by injectIoDispatcher()

	// Saves run here so they finish even when the screen that asked for one closes.
	private val scope by lazy { CoroutineScope(SupervisorJob() + dispatcherIo) }
	private val loadMutex = Mutex()
	private val saveMutex = Mutex()
	private val lock = reentrantLock()

	/** Null until the file has been read, which happens on first use rather than at construction. */
	private val state = MutableStateFlow<NuxState?>(null)

	/** Edits made before the file was read, applied on top of it once it is. Guarded by [lock]. */
	private var pendingEdits = emptyList<(NuxState) -> NuxState>()

	/** Those of [hints] still unseen, in the order given; none while the user has tips turned off. */
	fun unseen(hints: List<NuxHint>): Flow<List<NuxHint>> =
		combine(loadedState(), globalSettingsStore.globalSettingsUpdates) { state, settings ->
			if (settings.tipsEnabled) hints.filter { it.id !in state.seenHints } else emptyList()
		}.distinctUntilChanged()

	/** Starts a visit to a screen, rolling its one chance at offering tips after the first visit. */
	fun startVisit(): NuxVisit = NuxVisit(laterVisitOffers = random.nextInt(LATER_VISIT_ODDS) == 0)

	/**
	 * The hints [visit] may offer, from that screen's [hints] in order. While none of them has been
	 * seen, as on a first visit or after a reset, it offers them all; otherwise it offers what is left
	 * only if the visit's roll won. Hints for other platforms are left out. None while tips are off.
	 */
	fun visitHints(hints: List<NuxHint>, visit: NuxVisit): Flow<List<NuxHint>> {
		val applicable = hints.filter { platform in it.platforms }
		return unseen(applicable).map { unseen ->
			if (unseen.size == applicable.size || visit.laterVisitOffers) unseen else emptyList()
		}.distinctUntilChanged()
	}

	/** How many of the hints this platform can show have been seen, tips setting aside. */
	fun progress(): Flow<NuxProgress> {
		val applicable = NuxHint.entries.filter { platform in it.platforms }
		return loadedState().map { state ->
			NuxProgress(seen = applicable.count { it.id in state.seenHints }, total = applicable.size)
		}.distinctUntilChanged()
	}

	/** Whether [hint] is unseen right now, tips setting aside; false until the file has been read. */
	fun isUnseen(hint: NuxHint): Boolean = state.value?.let { hint.id !in it.seenHints } ?: false

	fun markSeen(hint: NuxHint) = edit { it.copy(seenHints = it.seenHints + hint.id) }

	/** Brings back every hint, turning tips on so they can show. */
	fun resetHints() {
		edit { it.copy(seenHints = emptySet()) }
		setTipsEnabled(true)
	}

	/** Saved from this scope, so it outlives the screen that asked. */
	fun setTipsEnabled(enabled: Boolean) {
		scope.launch { globalSettingsStore.updateSettings { it.copy(tipsEnabled = enabled) } }
	}

	private fun loadedState(): Flow<NuxState> = flow {
		ensureLoaded()
		emitAll(state.filterNotNull())
	}

	/** Applies [transform] in memory at once, then saves in the background. */
	private fun edit(transform: (NuxState) -> NuxState) {
		val changed = lock.withLock {
			val current = state.value
			if (current == null) {
				pendingEdits = pendingEdits + transform
				true
			} else {
				val next = transform(current)
				state.value = next
				next != current
			}
		}
		if (changed) scope.launch { save() }
	}

	private suspend fun ensureLoaded() = loadMutex.withLock {
		if (state.value != null) return@withLock
		val read = withContext(dispatcherIo) { readOrDefault() }
		lock.withLock {
			state.value = pendingEdits.fold(read) { current, edit -> edit(current) }
			pendingEdits = emptyList()
		}
	}

	private fun readOrDefault(): NuxState = try {
		datasource.load()
	} catch (e: IOException) {
		Napier.e("Failed to read NUX state, showing every hint.", e)
		NuxState()
	}

	/** Writes whatever the state is by the time it runs, so the last save leaves file and memory agreeing. */
	private suspend fun save() = saveMutex.withLock {
		ensureLoaded()
		val snapshot = state.value ?: return@withLock
		try {
			datasource.store(snapshot)
		} catch (e: IOException) {
			Napier.e("Failed to save NUX state.", e)
		}
	}

	companion object {
		/** A visit after the first offers the remaining tips once in this many. */
		const val LATER_VISIT_ODDS = 3
	}
}
