package com.darkrockstudios.apps.hammer.common.compose

import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlin.coroutines.cancellation.CancellationException

/**
 * Files picked from outside the app sandbox — iCloud on iOS, `content://` providers on Android —
 * are only readable through the scoped access granted at pick time. Copy the bytes into the app
 * cache now, while that scope is live, so downstream code can re-open the file by path. Returns
 * null if the copy fails.
 */
suspend fun PlatformFile.stageIntoCache(): PlatformFile? {
	return try {
		val bytes = readBytes()
		val localCopy = FileKit.cacheDir / name
		localCopy.write(bytes)
		localCopy
	} catch (e: CancellationException) {
		throw e
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		Napier.e("Failed to stage picked file into cache", e)
		null
	}
}
