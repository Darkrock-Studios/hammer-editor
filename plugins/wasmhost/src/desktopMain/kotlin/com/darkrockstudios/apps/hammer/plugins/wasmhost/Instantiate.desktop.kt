package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.error.ChasmError
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store
import kotlinx.coroutines.Job

internal actual val platformCompiles = false

internal actual fun compiledInstance(
	store: Store,
	module: Module,
	imports: List<Import>,
	cancellation: Job?,
): ChasmResult<Instance, ChasmError.ExecutionError> = throw UnsupportedOperationException("chasm compiles only on Android")

internal actual fun Throwable.isCodeLoadFailure(): Boolean = false
