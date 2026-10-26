package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.gc.GarbageCollectedHeap

// Kotlin/Native has no heap limit to read, so caps scaled to the host's heap take their fixed maximum here.
internal actual val hostHeapBytes: Long? = null

// Kotlin/Native throws this only for some failed allocations, such as an array too large; most abort the process.
internal actual fun Throwable.isOutOfMemory(): Boolean = this is OutOfMemoryError

// chasm offers no public way to build a store around a heap, and native code cannot reach the
// internal constructors the JVM does, so a plugin's guest heap is uncapped here.
internal actual fun storeWith(heap: GarbageCollectedHeap): Store? = null
