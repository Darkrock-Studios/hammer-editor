package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.aakira.napier.Napier
import io.github.charlietap.chasm.ExperimentalChasmApi
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store
import kotlinx.coroutines.runBlocking
import io.github.charlietap.chasm.jit.instance as compiledInstance

// chasm-jit compiles the module to DEX. It refuses the whole module when it cannot compile one function,
// such as one using SIMD, failing before anything is instantiated, so the interpreter runs those instead.
// It spreads compiling across cores but runs a share on the calling thread, so blocking that thread
// cannot starve it.
@OptIn(ExperimentalChasmApi::class)
internal actual fun instantiate(store: Store, module: Module, imports: List<Import>, compile: Boolean): Instantiated {
	if (!compile) return Instantiated(instance(store, module, imports), compiled = false)
	val compiled = runBlocking { compiledInstance(store, module, imports) }
	if (compiled is ChasmResult.Error && compiled.error.error.startsWith(JIT_FAILURE)) {
		Napier.w { "Plugin runs interpreted: ${compiled.error.error}" }
		return Instantiated(instance(store, module, imports), compiled = false)
	}
	return Instantiated(compiled, compiled = true)
}

/** How chasm-jit's own compile and load failures begin, as against the module failing to start. */
private const val JIT_FAILURE = "JIT "
