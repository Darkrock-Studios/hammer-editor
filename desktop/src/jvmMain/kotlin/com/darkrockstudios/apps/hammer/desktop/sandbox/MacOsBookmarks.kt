package com.darkrockstudios.apps.hammer.desktop.sandbox

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import io.github.aakira.napier.Napier

/**
 * JNA bridge over libhammer_bookmarks.dylib — the small Objective-C helper
 * that creates and resolves security-scoped NSURL bookmarks. The dylib is
 * only bundled on Mac App Store builds; on every other host [isAvailable]
 * is false and all calls return null / no-op.
 *
 * Pointer return values are heap-allocated C strings owned by the native
 * side — we read them as Java strings and then free via the matching
 * free function. The raw [Pointer] never escapes this file.
 */
internal interface HammerBookmarksLib : Library {
	fun hammer_bookmark_create(path: String): Pointer?
	fun hammer_bookmark_resolve_and_start(base64Bookmark: String, outStale: IntByReference): Pointer?
	fun hammer_bookmark_stop(path: String)
	fun hammer_bookmark_free_string(s: Pointer)
}

internal object MacOsBookmarks {

	/** Result of resolving a stored bookmark. */
	data class Resolved(val path: String, val isStale: Boolean)

	private const val LIB_NAME = "hammer_bookmarks"

	private val lib: HammerBookmarksLib? by lazy { tryLoadLib() }

	/** Canonical resolved path of the active extension; used as the stop key when switching dirs. */
	@Volatile
	var activePath: String? = null
		private set

	val isAvailable: Boolean get() = lib != null

	fun createBookmark(path: String): String? {
		val lib = lib ?: return null
		val ptr = lib.hammer_bookmark_create(path) ?: return null
		return try {
			ptr.getString(0)
		} finally {
			lib.hammer_bookmark_free_string(ptr)
		}
	}

	fun resolveAndStartAccess(base64Bookmark: String): Resolved? {
		val lib = lib ?: return null
		val stale = IntByReference(0)
		val ptr = lib.hammer_bookmark_resolve_and_start(base64Bookmark, stale) ?: return null
		return try {
			val resolved = Resolved(path = ptr.getString(0), isStale = stale.value != 0)
			activePath = resolved.path
			resolved
		} finally {
			lib.hammer_bookmark_free_string(ptr)
		}
	}

	fun stopAccess(path: String) {
		lib?.hammer_bookmark_stop(path)
		if (activePath == path) activePath = null
	}

	private fun tryLoadLib(): HammerBookmarksLib? {
		val osName = System.getProperty("os.name") ?: return null
		if (!osName.lowercase().contains("mac")) return null
		return try {
			Native.load(LIB_NAME, HammerBookmarksLib::class.java)
		} catch (@Suppress("TooGenericExceptionCaught") t: Throwable) { // native load failure is a graceful no-op fallback
			Napier.w("Could not load libhammer_bookmarks.dylib — sandbox bookmarks disabled", t)
			null
		}
	}
}
