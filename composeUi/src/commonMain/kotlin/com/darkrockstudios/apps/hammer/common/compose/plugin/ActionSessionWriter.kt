package com.darkrockstudios.apps.hammer.common.compose.plugin

import com.darkrockstudios.apps.hammer.operations.plugin.ActionSession
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionException
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionKey
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionStore
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Saves and removes kept runs in [store] off the main thread, one at a time. Only the last change
 * asked of each session is written, and a load or [flush] first writes every change still waiting,
 * so neither ever sees an older one.
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

	private class Change(val run: ActionRun, val session: ActionSession?)

	fun save(run: ActionRun, key: ActionSessionKey, session: ActionSession) = ask(key, Change(run, session))

	fun remove(run: ActionRun, key: ActionSessionKey) = ask(key, Change(run, null))

	/** Blocks; call it off the main thread. Throws what [ActionSessionStore.load] does. */
	fun load(key: ActionSessionKey): ActionSession? = writing.withLock {
		writeWaiting()
		store.load(key)
	}

	/** Writes every change still waiting, on the calling thread. */
	fun flush() = writing.withLock { writeWaiting() }

	private fun ask(key: ActionSessionKey, change: Change) {
		queue.withLock { waiting[key] = change }
		scope.launch(dispatcher) { flush() }
	}

	private fun next(): Pair<ActionSessionKey, Change>? = queue.withLock {
		val key = waiting.keys.firstOrNull() ?: return null
		key to waiting.remove(key)!!
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
