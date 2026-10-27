package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Held from beginProjectsSync to endProjectsSync. The server keeps one account sync session per
 * install and lets that install's next begin replace it, so a second session opened from this
 * process would take over the first one's partway through.
 *
 * Not reentrant: nothing holding it may open another session.
 */
class AccountSessionLock {
	@PublishedApi
	internal val mutex = Mutex()

	suspend inline fun <T> withSession(block: () -> T): T = mutex.withLock(action = block)
}
