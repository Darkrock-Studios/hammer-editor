package com.darkrockstudios.apps.hammer.plugins.wasmhost

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy

private const val IDLE_TIMEOUT_SECONDS = 30.0
private const val TOTAL_TIMEOUT_SECONDS = 60.0
private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

// A redirect is returned to the plugin rather than followed, as on the JVM, since it could lead to a
// host the plugin may not reach.
private class NoRedirects : NSObject(), NSURLSessionTaskDelegateProtocol {
	@ObjCSignatureOverride
	override fun URLSession(
		session: NSURLSession,
		task: NSURLSessionTask,
		willPerformHTTPRedirection: NSHTTPURLResponse,
		newRequest: NSURLRequest,
		completionHandler: (NSURLRequest?) -> Unit,
	) {
		completionHandler(null)
	}
}

// Ephemeral: no cookies, cache, or credentials carried from one plugin's request to another's.
private val session: NSURLSession by lazy {
	val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration.apply {
		timeoutIntervalForRequest = IDLE_TIMEOUT_SECONDS
		timeoutIntervalForResource = TOTAL_TIMEOUT_SECONDS
	}
	NSURLSession.sessionWithConfiguration(configuration, delegate = NoRedirects(), delegateQueue = null)
}

// The host calls this off the main thread, so waiting on the task blocks only the plugin's call.
internal actual val platformPluginHttp: PluginHttp? = PluginHttp { request ->
	val url = NSURL.URLWithString(request.url) ?: error("Invalid URL: ${request.url}")
	val urlRequest = NSMutableURLRequest.requestWithURL(url, NSURLRequestReloadIgnoringLocalCacheData, IDLE_TIMEOUT_SECONDS)
	urlRequest.setHTTPMethod(request.method.uppercase())
	request.headers.forEach { (name, value) -> urlRequest.setValue(value, forHTTPHeaderField = name) }
	request.body?.let { urlRequest.setHTTPBody(it.toNSData()) }

	var status: Int? = null
	var body: ByteArray? = null
	var failure: String? = null
	val done = dispatch_semaphore_create(0)
	session.dataTaskWithRequest(urlRequest) { data, response, error ->
		if (error != null) {
			failure = error.localizedDescription
		} else {
			status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
			body = data?.toByteArray()
		}
		dispatch_semaphore_signal(done)
	}.resume()
	dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER)

	failure?.let { error("Request to ${request.url} failed: $it") }
	val bytes = body ?: ByteArray(0)
	if (bytes.size > MAX_RESPONSE_BYTES) error("Response from ${request.url} is over ${MAX_RESPONSE_BYTES / 1024 / 1024} MiB")
	PluginHttpResponse(status ?: error("No HTTP response from ${request.url}"), bytes)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
	val size = length.toInt()
	val out = ByteArray(size)
	if (size == 0) return out
	out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length.convert()) }
	return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
	if (isEmpty()) return NSData()
	return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.convert()) }
}
