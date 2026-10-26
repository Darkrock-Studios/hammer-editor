package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.gc.GarbageCollectedHeap

/**
 * The heap where a Wasm GC module, such as a Kotlin/Wasm one, keeps its objects, capped at [maxBytes] or
 * half the host's own heap if that is less, and the store around it. chasm's default heap is uncapped.
 * A smaller share would leave a phone unable to report on a novel; running the host out of memory past
 * this fails the plugin, not the app.
 */
internal class GuestHeap(maxBytes: Long) {
	private val heap = GarbageCollectedHeap(
		GarbageCollectedHeap.Configuration(
			maximumPageCount = maxOf(1, (hostHeapShare(maxBytes, HOST_HEAP_SHARE) / PAGE_BYTES).toInt())
		)
	)

	/** A store using this heap, or null where the host cannot build one, which then needs an uncapped store. */
	val store: Store? = storeWith(heap)

	/** What the heap holds from the host, pages it keeps for reuse included: it never gives them back. */
	val committedBytes: Long
		get() = heap.snapshotStatistics().let { (it.committedWords + it.dedicatedPayloadWords) * WORD_BYTES }

	private companion object {
		const val HOST_HEAP_SHARE = 2
		const val WORD_BYTES = 8L

		/** chasm's guest heap is paged in 2,048 words. */
		const val PAGE_BYTES = 2048 * WORD_BYTES
	}
}

/** The host's own heap limit, or null where it has none to speak of. */
internal expect val hostHeapBytes: Long?

/** [maxBytes], or the host's heap divided by [share] if that is less. */
internal fun hostHeapShare(maxBytes: Long, share: Int): Long = minOf(maxBytes, (hostHeapBytes ?: Long.MAX_VALUE) / share)

/**
 * Whether this is the host running out of memory, which a plugin can cause and so must not end the app.
 * Common code cannot name the error.
 */
internal expect fun Throwable.isOutOfMemory(): Boolean

internal expect fun storeWith(heap: GarbageCollectedHeap): Store?
