package com.darkrockstudios.apps.hammer.common.data.search

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_RETAINED = 8

/**
 * Lends [MarkdownProjector]s to concurrent scans and keeps them afterwards, so the buffers a scan
 * grew are still there for the next keystroke rather than being rebuilt from scratch.
 *
 * A projector cannot be a plain field on the use case: cancelling a search is cooperative, so the
 * outgoing scan can still be running when the next one starts, and the two would share a buffer.
 * Borrowing serializes that without forcing the scans themselves to run one at a time.
 */
class MarkdownProjectorPool {

	private val mutex = Mutex()
	private val available = mutableListOf<MarkdownProjector>()

	suspend fun <T> borrow(block: suspend (MarkdownProjector) -> T): T {
		val projector = mutex.withLock { available.removeLastOrNull() } ?: MarkdownProjector()
		try {
			return block(projector)
		} finally {
			mutex.withLock {
				if (available.size < MAX_RETAINED) available.add(projector)
			}
		}
	}
}
