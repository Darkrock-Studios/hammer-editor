package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.gc.GarbageCollectedHeap

internal actual val hostHeapBytes: Long? = null

// chasm offers no public way to build a store around a heap, and native code cannot reach the
// internal constructors the JVM does.
internal actual fun storeWith(heap: GarbageCollectedHeap): Store? = null
