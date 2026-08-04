package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.url
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.network_request_failure_parse_body
import com.darkrockstudios.apps.hammer.server_error_connection_generic
import com.darkrockstudios.apps.hammer.server_error_connection_timeout
import com.darkrockstudios.apps.hammer.server_error_dns
import com.darkrockstudios.apps.hammer.server_error_timeout
import com.darkrockstudios.apps.hammer.server_error_tls
import com.darkrockstudios.apps.hammer.sync_general_error
import com.darkrockstudios.apps.hammer.sync_unauthorized
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.withContext
import okio.IOException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class Api(
	private val httpClient: HttpClient,
	private val globalSettingsStore: GlobalSettingsStore,
	private val strRes: StrRes,
) : KoinComponent {

	protected suspend fun defaultFailure(response: HttpResponse): Throwable =
		defaultFailureHandler(response, strRes)
	protected val userId: Long?
		get() = globalSettingsStore.serverSettings?.userId

	private val ioDispatcher by injectIoDispatcher()
	private val localeResolver: DeviceLocaleResolver by inject()
	private val protocolMismatchRepository: ProtocolMismatchRepository by inject()

	private suspend fun <T> makeRequest(
		path: String,
		builder: HttpRequestBuilder.() -> Unit = {},
		execute: suspend (block: HttpRequestBuilder.() -> Unit) -> HttpResponse,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		parse: suspend (HttpResponse) -> T,
	): Result<T> = withContext(ioDispatcher) {
		val server = globalSettingsStore.serverSettings ?: return@withContext Result.failure<T>(
			IllegalStateException("Server not configured")
		)

		var outerResponse: HttpResponse? = null
		return@withContext try {
			val response = execute {
				header("Accept-Language", localeResolver.getCurrentLocale().toLanguageTag())
				url(server, path)
				builder()
			}
			outerResponse = response

			if (response.status.isSuccess()) {
				val value = parse(response)
				Result.success(value)
			} else {
				if (response.status == HttpStatusCode.UpgradeRequired) {
					protocolMismatchRepository.notifyMismatch(
						clientProtocolVersion = HAMMER_PROTOCOL_VERSION,
						serverProtocolVersion = response.headers[HAMMER_PROTOCOL_HEADER]?.toIntOrNull(),
					)
				}
				Result.failure(
					failureHandler(response)
				)
			}
		} catch (e: NoTransformationFoundException) {
			Napier.e("Failed to parse error response", e)
			Result.failure(
				HttpFailureException(
					statusCode = outerResponse?.status ?: HttpStatusCode.ExpectationFailed,
					error = HttpResponseError(
						error = "Failed to parse error response",
						displayMessage = strRes.get(Res.string.network_request_failure_parse_body, path),
					)
				)
			)
		} catch (e: ConnectTimeoutException) {
			// Connection timeout - server not responding when connecting
			Napier.e("Connection timeout", e)
			Result.failure(
				HttpFailureException(
					statusCode = HttpStatusCode.RequestTimeout,
					error = HttpResponseError(
						error = e.message ?: "Connection Timeout",
						displayMessage = strRes.get(Res.string.server_error_connection_timeout),
					)
				)
			)
		} catch (e: SocketTimeoutException) {
			// Socket timeout - server stopped responding during data transfer
			Napier.e("Socket timeout", e)
			Result.failure(
				HttpFailureException(
					statusCode = HttpStatusCode.RequestTimeout,
					error = HttpResponseError(
						error = e.message ?: "Socket Timeout",
						displayMessage = strRes.get(Res.string.server_error_timeout),
					)
				)
			)
		} catch (e: HttpRequestTimeoutException) {
			// Overall request timeout
			Napier.e("Request timeout", e)
			Result.failure(
				HttpFailureException(
					statusCode = HttpStatusCode.RequestTimeout,
					error = HttpResponseError(
						error = e.message ?: "Request Timeout",
						displayMessage = strRes.get(Res.string.server_error_timeout),
					)
				)
			)
		} catch (e: UnresolvedAddressException) {
			// DNS resolution failure
			Napier.e("Unresolved address", e)
			Result.failure(
				HttpFailureException(
					statusCode = HttpStatusCode.BadGateway,
					error = HttpResponseError(
						error = e.message ?: "Unresolved Address",
						displayMessage = strRes.get(Res.string.server_error_dns),
					)
				)
			)
		} catch (e: IOException) {
			if (e.isTlsFailure()) {
				Napier.e("TLS Error", e)
				Result.failure(
					HttpFailureException(
						statusCode = outerResponse?.status ?: HttpStatusCode.BadGateway,
						error = HttpResponseError(
							error = e.message ?: "TLS Error",
							displayMessage = strRes.get(Res.string.server_error_tls, server.url),
						)
					)
				)
			} else {
				// Generic network error (connection refused, etc.)
				Napier.e("Network Error", e)
				Result.failure(
					HttpFailureException(
						statusCode = outerResponse?.status ?: HttpStatusCode.RequestTimeout,
						error = HttpResponseError(
							error = e.message ?: "Network Error",
							displayMessage = strRes.get(Res.string.server_error_connection_generic, path),
						)
					)
				)
			}
		}
	}

	protected suspend fun post(
		path: String,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		builder: HttpRequestBuilder.() -> Unit = {},
	): Result<String> =
		makeRequest(
			path = path,
			builder = builder,
			execute = httpClient::post,
			parse = { it.bodyAsText() },
			failureHandler = failureHandler
		)

	protected suspend fun <T> post(
		path: String,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		parse: suspend (HttpResponse) -> T,
		builder: HttpRequestBuilder.() -> Unit = {},
	): Result<T> =
		makeRequest(
			path = path,
			builder = builder,
			execute = httpClient::post,
			parse = parse,
			failureHandler = failureHandler
		)

	/**
	 * POST first, retrying once as GET when the server answers 404/405: servers that predate
	 * the POST migration only route these endpoints as GET. A genuine 404 from a modern server
	 * costs one redundant GET that fails the same way, so the result is still correct.
	 */
	// TODO Remove the GET fallback (use plain post) at the next protocol version bump.
	protected suspend fun <T> postWithLegacyGetFallback(
		path: String,
		parse: suspend (HttpResponse) -> T,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		builder: HttpRequestBuilder.() -> Unit = {},
	): Result<T> {
		val postResult = makeRequest(
			path = path,
			builder = builder,
			execute = httpClient::post,
			parse = parse,
			failureHandler = failureHandler,
		)

		val failure = postResult.exceptionOrNull()
		val serverLacksPostRoute = failure is HttpFailureException &&
			(failure.statusCode == HttpStatusCode.NotFound || failure.statusCode == HttpStatusCode.MethodNotAllowed)

		return if (serverLacksPostRoute) {
			makeRequest(
				path = path,
				builder = builder,
				execute = httpClient::get,
				parse = parse,
				failureHandler = failureHandler,
			)
		} else {
			postResult
		}
	}

	protected suspend fun get(
		path: String,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		builder: HttpRequestBuilder.() -> Unit = {},
	): Result<String> =
		makeRequest(
			path = path,
			builder = builder,
			execute = httpClient::get,
			parse = { it.bodyAsText() },
			failureHandler = failureHandler
		)

	protected suspend fun <T> get(
		path: String,
		parse: suspend (HttpResponse) -> T,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		builder: HttpRequestBuilder.() -> Unit = {},
	): Result<T> =
		makeRequest(
			path = path,
			builder = builder,
			execute = httpClient::get,
			parse = parse,
			failureHandler = failureHandler
		)

	protected suspend fun put(
		path: String,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		builder: HttpRequestBuilder.() -> Unit = {},
	): Result<String> =
		makeRequest(
			path = path,
			builder = builder,
			execute = httpClient::put,
			parse = { it.bodyAsText() },
			failureHandler = failureHandler
		)

	protected suspend fun <T> put(
		path: String,
		parse: suspend (HttpResponse) -> T,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		builder: HttpRequestBuilder.() -> Unit = {},
	): Result<T> =
		makeRequest(
			path = path,
			builder = builder,
			execute = httpClient::put,
			parse = parse,
			failureHandler = failureHandler
		)

	protected suspend fun delete(
		path: String,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		builder: HttpRequestBuilder.() -> Unit = {},
	): Result<String> =
		makeRequest(
			path = path,
			builder = builder,
			execute = httpClient::delete,
			parse = { it.bodyAsText() },
			failureHandler = failureHandler
		)

	protected suspend fun <T> delete(
		path: String,
		parse: suspend (HttpResponse) -> T,
		failureHandler: FailureHandler = { defaultFailureHandler(it, strRes) },
		builder: HttpRequestBuilder.() -> Unit = {},
	): Result<T> =
		makeRequest(
			path = path,
			builder = builder,
			execute = httpClient::delete,
			parse = parse,
			failureHandler = failureHandler
		)
}

class HttpFailureException(
	val statusCode: HttpStatusCode,
	val error: HttpResponseError
) : Exception("HTTP $statusCode ${error.error}: ${error.displayMessage}") {
	override fun toString() = message ?: super.toString()
}

/** The server requires the given Terms of Service to be accepted before an account can be created. */
class TermsOfServiceRequiredException(
	val challenge: TermsOfServiceChallenge
) : Exception("Terms of service acceptance required (version ${challenge.version})")

typealias FailureHandler = suspend (HttpResponse) -> Throwable

suspend fun defaultFailureHandler(response: HttpResponse, strRes: StrRes): Throwable {
	val error = try {
		response.body<HttpResponseError>()
	} catch (e: NoTransformationFoundException) {
		null.also { Napier.w("Error response body unable to be parsed", e) }
	} catch (e: ContentConvertException) {
		null.also { Napier.w("Error response body unable to be parsed", e) }
	}

	if (error != null) return HttpFailureException(statusCode = response.status, error = error)

	// Only when the server said nothing usable. A 401 carrying "invalid email or
	// password" must reach the user as that, not as the generic sync message.
	return HttpFailureException(
		statusCode = response.status,
		error = HttpResponseError(
			error = if (response.status == HttpStatusCode.Unauthorized) "Unauthorized" else "Unhandled error body",
			displayMessage = if (response.status == HttpStatusCode.Unauthorized) {
				strRes.get(Res.string.sync_unauthorized)
			} else {
				strRes.get(Res.string.sync_general_error)
			},
		)
	)
}