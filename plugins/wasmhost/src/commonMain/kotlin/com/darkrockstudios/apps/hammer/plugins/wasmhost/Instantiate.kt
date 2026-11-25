package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.error.ChasmError
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store

/** An instance, or why it could not start, and whether its code was compiled rather than interpreted. */
internal class Instantiated(val result: ChasmResult<Instance, ChasmError.ExecutionError>, val compiled: Boolean)

/** Instantiates [module] in [store], compiled to the platform's own code when [compile] asks and the platform can. */
internal expect fun instantiate(store: Store, module: Module, imports: List<Import>, compile: Boolean): Instantiated
