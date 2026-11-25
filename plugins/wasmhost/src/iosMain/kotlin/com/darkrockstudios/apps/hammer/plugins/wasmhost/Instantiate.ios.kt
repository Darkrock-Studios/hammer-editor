package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store

internal actual fun instantiate(store: Store, module: Module, imports: List<Import>, compile: Boolean): Instantiated =
	Instantiated(instance(store, module, imports), compiled = false)
