package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.config.StoreConfig
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.gc.GarbageCollectedHeap

/**
 * The heap where a Wasm GC module, such as a Kotlin/Wasm one, keeps its objects, capped at [maxBytes] or
 * half the host's own heap if that is less, and the store around it. chasm's default heap is uncapped.
 * A smaller share would leave a phone unable to report on a novel; running the host out of memory past
 * this fails the plugin, not the app.
 */
internal class GuestHeap(maxBytes: Long, config: StoreConfig) {
	private val capBytes = hostHeapShare(maxBytes, HOST_HEAP_SHARE)
	private val heap = GarbageCollectedHeap(
		GarbageCollectedHeap.Configuration(maximumPageCount = maxOf(1, (capBytes / PAGE_BYTES).toInt()))
	)
	private val hostStore = storeWith(heap, config)

	/** A store around this heap, or where the host cannot build one, a store whose heap chasm caps the same. */
	val store: Store = hostStore ?: store(config.copy(limits = config.limits.copy(gcHeapBytes = capBytes)))

	/**
	 * What the heap holds from the host, pages it keeps for reuse included: it never gives them back.
	 * 0 where chasm keeps the heap, which the host cannot measure.
	 */
	val committedBytes: Long
		get() = if (hostStore == null) 0 else heap.snapshotStatistics().let { (it.committedWords + it.dedicatedPayloadWords) * WORD_BYTES }

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

/** A store around [heap], or null where the host cannot build one. */
internal expect fun storeWith(heap: GarbageCollectedHeap, config: StoreConfig): Store?
