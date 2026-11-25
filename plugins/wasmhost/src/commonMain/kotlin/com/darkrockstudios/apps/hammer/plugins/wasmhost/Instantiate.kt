package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.error.ChasmError
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store

/** Compiles a module to the platform's own code as it instantiates it. */
fun interface ModuleCompiler {
	fun instantiate(store: Store, module: Module, imports: List<Import>): CompiledStart
}

sealed interface CompiledStart {
	/** The module started, or failed to start for a reason of its own. */
	class Started(val result: ChasmResult<Instance, ChasmError.ExecutionError>) : CompiledStart

	/** The compiler could not compile or load the module, which should run interpreted. */
	class Refused(val reason: String) : CompiledStart
}

/** chasm's compiler for this platform, or null where chasm only interprets. */
internal expect val platformCompiler: ModuleCompiler?

/** Whether this is compiled code failing to load, which a call may find only when it first reaches that code. */
internal expect fun Throwable.isCodeLoadFailure(): Boolean
