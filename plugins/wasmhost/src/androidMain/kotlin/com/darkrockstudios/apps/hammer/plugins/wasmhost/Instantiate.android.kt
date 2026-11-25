package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.ExperimentalChasmApi
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store
import kotlinx.coroutines.runBlocking
import io.github.charlietap.chasm.jit.instance as jitInstance

internal actual val platformCompiles = true

// chasm-jit compiles the module to DEX. It refuses the whole module when it cannot compile one function,
// such as one with more locals than a DEX method has registers, failing before anything is instantiated;
// a DEX that ART will not load fails after the module is allocated. chasm-jit spreads compiling across
// cores but runs a share on the calling thread, so blocking that thread cannot starve it.
@OptIn(ExperimentalChasmApi::class)
internal actual fun compiledInstance(store: Store, module: Module, imports: List<Import>): CompiledStart = try {
	val result = runBlocking { jitInstance(store, module, imports) }
	if (result is ChasmResult.Error && result.error.error.startsWith(JIT_FAILURE)) {
		CompiledStart.Refused(result.error.error)
	} else {
		CompiledStart.Started(result)
	}
} catch (e: OutOfMemoryError) {
	CompiledStart.Refused(e.toString())
} catch (e: LinkageError) {
	CompiledStart.Refused(e.toString())
}

internal actual fun Throwable.isCodeLoadFailure(): Boolean = this is LinkageError

/** How chasm-jit's own compile and load failures begin, as against the module failing to start. */
private const val JIT_FAILURE = "JIT "
