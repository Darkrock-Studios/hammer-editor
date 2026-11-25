package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.error.ChasmError
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store
import kotlinx.coroutines.Job

/** Whether chasm can compile modules to this platform's own code. */
internal expect val platformCompiles: Boolean

/**
 * Instantiates [module] in [store] with its code compiled, where [platformCompiles]. Cancelling
 * [cancellation] stops compiling. An error beginning [JIT_FAILURE] is the compiler's, not the module's.
 */
internal expect fun compiledInstance(
	store: Store,
	module: Module,
	imports: List<Import>,
	cancellation: Job?,
): ChasmResult<Instance, ChasmError.ExecutionError>

/** Whether this is compiled code failing to load, which a call may find only when it first reaches that code. */
internal expect fun Throwable.isCodeLoadFailure(): Boolean

/** How chasm-jit's own compile and load failures begin. */
internal const val JIT_FAILURE = "JIT "
