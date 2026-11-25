package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store

internal actual val platformCompiles = false

internal actual fun compiledInstance(store: Store, module: Module, imports: List<Import>): CompiledStart =
	throw UnsupportedOperationException("chasm compiles only on Android")

internal actual fun Throwable.isCodeLoadFailure(): Boolean = false
