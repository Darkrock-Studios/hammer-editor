package com.darkrockstudios.apps.hammer.plugins.wasmhost

internal actual val platformCompiler: ModuleCompiler? = null

internal actual fun Throwable.isCodeLoadFailure(): Boolean = false
