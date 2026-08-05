package com.darkrockstudios.apps.hammer.common.server

/**
 * NSURLSession surfaces TLS problems as an `NSError` whose localized description names the
 * failure, which is all the Darwin engine carries through into the exception message.
 */
private val TLS_MARKERS = listOf("ssl", "tls", "certificate", "secure connection")

actual fun Throwable.isTlsFailure(): Boolean = causeChain().any { throwable ->
	val text = "${throwable::class.simpleName.orEmpty()} ${throwable.message.orEmpty()}"
	TLS_MARKERS.any { marker -> text.contains(marker, ignoreCase = true) }
}
