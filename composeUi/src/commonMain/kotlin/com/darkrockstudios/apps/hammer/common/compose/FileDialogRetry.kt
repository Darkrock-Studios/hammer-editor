package com.darkrockstudios.apps.hammer.common.compose

import io.github.aakira.napier.Napier
import kotlin.coroutines.cancellation.CancellationException

private const val FILE_DIALOG_ATTEMPTS = 4

// thar be dragons: FileKit's Windows backend calls CoInitializeEx on a shared Dispatchers.IO
// thread that may already be COM-initialized in a conflicting apartment, throwing
// "CoInitializeEx failed". Its teardown uninitializes that thread on failure, so retrying
// succeeds. Returns null rather than letting the dialog crash the app.
suspend fun <T> retryingFileDialog(block: suspend () -> T?): T? {
	var lastError: RuntimeException? = null
	repeat(FILE_DIALOG_ATTEMPTS) {
		try {
			return block()
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
			lastError = e
		}
	}
	Napier.e("File dialog failed after $FILE_DIALOG_ATTEMPTS attempts", lastError)
	return null
}
