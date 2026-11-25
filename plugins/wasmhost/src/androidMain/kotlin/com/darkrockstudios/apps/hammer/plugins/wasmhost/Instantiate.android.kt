package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.ExperimentalChasmApi
import io.github.charlietap.chasm.embedding.error.ChasmError
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import io.github.charlietap.chasm.jit.instance as jitInstance

// chasm-jit compiles the module to DEX. It refuses the whole module when it cannot compile one function,
// such as one past DEX's limits on arguments or registers, failing before anything is instantiated.
internal actual val platformCompiles = true

// chasm-jit spreads compiling across cores but runs a share on the calling thread, so blocking that
// thread cannot starve it. The supervisor lets cancelling [cancellation] stop compiling while a failure
// here, such as running out of memory, does not cancel [cancellation] in turn.
@OptIn(ExperimentalChasmApi::class)
internal actual fun compiledInstance(
	store: Store,
	module: Module,
	imports: List<Import>,
	cancellation: Job?,
): ChasmResult<Instance, ChasmError.ExecutionError> {
	val link = SupervisorJob(cancellation)
	try {
		return runBlocking(link) { jitInstance(store, module, imports) }
	} finally {
		link.complete()
	}
}

internal actual fun Throwable.isCodeLoadFailure(): Boolean = this is LinkageError
