package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.gc.GarbageCollectedHeap
import io.github.charlietap.chasm.runtime.heap.WasmHeap
import io.github.charlietap.chasm.runtime.store.Fuel
import io.github.charlietap.chasm.runtime.store.Store as RuntimeStore

internal actual val hostHeapBytes: Long? = Runtime.getRuntime().maxMemory().takeIf { it != Long.MAX_VALUE }

// chasm 2.0.0 keeps the constructors that take a heap internal to Kotlin; on the JVM they are public.
internal actual fun storeWith(heap: GarbageCollectedHeap): Store? {
	val wasmHeap = WasmHeap::class.java.getConstructor(GarbageCollectedHeap::class.java).newInstance(heap)
	return Store::class.java.getConstructor(RuntimeStore::class.java).newInstance(RuntimeStore(heap = wasmHeap, fuel = Fuel(metered = true)))
}
