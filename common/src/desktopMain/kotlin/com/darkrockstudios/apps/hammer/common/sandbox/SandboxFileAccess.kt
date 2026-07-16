package com.darkrockstudios.apps.hammer.common.sandbox

import org.koin.core.annotation.Provided

/**
 * Abstraction over macOS security-scoped bookmark APIs used to persist
 * access to a user-picked directory across app launches in the sandboxed
 * Mac App Store flavor. The implementation lives in the desktop module
 * (it's JNA + a bundled Obj-C dylib); common code talks to it through
 * this interface so it can no-op on non-sandboxed builds.
 *
 * On Linux/Windows/non-App-Store desktop builds, [NoopSandboxFileAccess]
 * is bound — [isSandboxed] is false and all calls return null / do nothing.
 *
 * [Provided]: the Koin definition lives in the desktop app module, so it is
 * external to this module's compile-time dependency graph.
 */
@Provided
interface SandboxFileAccess {
	/** True only on Mac App Store builds with the bookmark dylib successfully loaded. */
	val isSandboxed: Boolean

	/**
	 * Persists access to [path] for future launches and activates a fresh
	 * sandbox extension, replacing any prior one. Returns false on bookmark
	 * failure — callers should abort the directory change rather than
	 * commit settings the user can't recover from.
	 */
	fun establishAccessForNewDirectory(path: String): Boolean
}

object NoopSandboxFileAccess : SandboxFileAccess {
	override val isSandboxed: Boolean = false
	override fun establishAccessForNewDirectory(path: String): Boolean = true
}
