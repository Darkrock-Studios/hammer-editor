package com.darkrockstudios.apps.hammer.common.compose.plugin

import com.darkrockstudios.apps.hammer.operations.plugin.ActionSession
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionException
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionKey
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionStore
import com.darkrockstudios.apps.hammer.operations.plugin.EntityRenumbering
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Saves and removes kept runs in [store] off the main thread, one at a time. Only the last change
 * asked of each session is written, and a load or [flush] first writes every change still waiting,
 * so neither ever sees an older one. A change asked before the runner had [followed] a sync's new ids
 * is given them as it is written, so it cannot file a run under, or pick, an id now another item's.
 */
internal class ActionSessionWriter(
	private val store: ActionSessionStore,
	private val scope: CoroutineScope,
	private val dispatcher: CoroutineContext,
	/** Told of a save that failed; on the thread that tried it. */
	private val onSaveFailed: (ActionRun, ActionSessionException) -> Unit,
) {
	// Guards waiting only, so asking for a change never waits on the disk.
	private val queue = reentrantLock()

	// Held while writing, so changes reach the disk one at a time and in the order they were asked.
	private val writing = reentrantLock()

	private val waiting = LinkedHashMap<ActionSessionKey, Change>()

	// The project's ids sync has changed, in order, and how many of them the runner had followed when it
	// asked each change. Guarded by queue.
	private val renumberings = mutableListOf<EntityRenumbering>()
	private var followed = 0

	private class Change(val run: ActionRun, val session: ActionSession?, val followed: Int)

	fun save(run: ActionRun, key: ActionSessionKey, session: ActionSession) = ask(key, run, session)

	fun remove(run: ActionRun, key: ActionSessionKey) = ask(key, run, null)

	/**
	 * Runs [pass], which follows [renumberings] in the sessions on disk, with no change written meanwhile;
	 * on the thread syncing. Each change waiting, or asked before the runner has [followed] them, is given
	 * them when it is written.
	 */
	fun hold(renumberings: List<EntityRenumbering>, pass: () -> Unit) = writing.withLock {
		queue.withLock { this.renumberings += renumberings }
		pass()
	}

	/** The runner has followed [renumberings] in what it holds, so changes it asks from now have them. */
	fun followed(renumberings: List<EntityRenumbering>) = queue.withLock { followed += renumberings.size }

	/** Blocks; call it off the main thread. Throws what [ActionSessionStore.load] does. */
	fun load(key: ActionSessionKey): ActionSession? = writing.withLock {
		writeWaiting()
		store.load(key)
	}

	/** Writes every change still waiting, on the calling thread. */
	fun flush() = writing.withLock { writeWaiting() }

	private fun ask(key: ActionSessionKey, run: ActionRun, session: ActionSession?) {
		queue.withLock { waiting[key] = Change(run, session, followed) }
		scope.launch(dispatcher) { flush() }
	}

	/** The next change waiting, given the new ids it was asked without. */
	private fun next(): Pair<ActionSessionKey, Change>? = queue.withLock {
		val key = waiting.keys.firstOrNull() ?: return null
		val change = waiting.remove(key)!!
		renumberings.drop(change.followed).fold(key to change) { (key, change), renumbering ->
			renumbering.key(key) to Change(change.run, change.session?.let(renumbering::session), change.followed)
		}
	}

	private fun writeWaiting() {
		while (true) {
			val (key, change) = next() ?: return
			val session = change.session
			try {
				if (session != null) store.save(key, session) else store.remove(key)
			} catch (e: ActionSessionException) {
				if (session != null) {
					onSaveFailed(change.run, e)
				} else {
					// Start over offers a way out of a session that outlived its run.
					Napier.e(e) { "Could not remove a finished run of '${key.action}'" }
				}
			}
		}
	}
}
