package com.darkrockstudios.apps.hammer.common

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun debugBuild() {
	// Install the file logger so logs are persisted to disk (and still echoed to the
	// console, since FileLogger delegates to DebugAntilog). The scope lives for the
	// lifetime of the app, matching Android's applicationScope.
	val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	Napier.base(FileLogger(scope = scope))
}
