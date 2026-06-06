package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.url
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.network.*
import kotlinx.coroutines.withContext
import okio.IOException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class Api(
	private val httpClient: HttpClient,
	private val globalSettingsStore: GlobalSettingsStore,
	private val strRes: StrRes,
) : KoinComponent {
	protected val userId: Long?
		get() = globalSettingsStore.serverSettings?.userId

	private val ioDispatcher by injectIoDispatcher()
	private val localeResolver: DeviceLocaleResolver by inject()

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
				header("Accept-Language", localeResolver.getCurrentLocale().toLanguageTag().toString())
				url(server, path)
				builder()
			}
			outerResponse = response

			if (response.status.isSuccess()) {
				val value = parse(response)
				Result.success(value)
			} else {
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
			// Generic network error (connection refused, SSL errors, etc.)
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

typealias FailureHandler = suspend (HttpResponse) -> Throwable

suspend fun defaultFailureHandler(response: HttpResponse, strRes: StrRes): Throwable {
	return when(response.status) {
		HttpStatusCode.Unauthorized -> {
			HttpFailureException(
				statusCode = response.status,
				error = HttpResponseError(
					error = "Unauthorized",
					displayMessage = strRes.get(Res.string.sync_unauthorized),
				)
			)
		}
		else -> {
			try {
				val error = response.body<HttpResponseError>()
				HttpFailureException(
					statusCode = response.status,
					error = error
				)
			} catch (e: NoTransformationFoundException) {
				Napier.w("Error response body unable to be parsed", e)
				HttpFailureException(
					statusCode = response.status,
					error = HttpResponseError(
						error = "Unhandled error body",
						displayMessage = strRes.get(Res.string.sync_general_error),
					)
				)
			}
		}
	}
}