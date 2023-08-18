package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.url
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withContext
import okio.IOException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class Api(
	private val httpClient: HttpClient,
	private val globalSettingsRepository: GlobalSettingsRepository
) : KoinComponent {
	protected val userId: Long?
		get() = globalSettingsRepository.serverSettings?.userId

	private val ioDispatcher by injectIoDispatcher()
	private val localeResolver: DeviceLocaleResolver by inject()

	private suspend fun <T> makeRequest(
		path: String,
		builder: HttpRequestBuilder.() -> Unit = {},
		execute: suspend (block: HttpRequestBuilder.() -> Unit) -> HttpResponse,
		failureHandler: FailureHandler = { defaultFailureHandler(it) },
		parse: suspend (HttpResponse) -> T,
	): Result<T> = withContext(ioDispatcher) {
		val server = globalSettingsRepository.serverSettings ?: return@withContext Result.failure<T>(
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
					statusCode = outerResponse?.status ?: HttpStatusCode.InternalServerError,
					error = HttpResponseError(
						error = "Failed to parse error response",
						displayMessage = "Unknown Error",
					)
				)
			)
		} catch (e: IOException) {
			Result.failure(e)
		}
	}

	protected suspend fun post(
		path: String,
		failureHandler: FailureHandler = { defaultFailureHandler(it) },
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
		failureHandler: FailureHandler = { defaultFailureHandler(it) },
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
		failureHandler: FailureHandler = { defaultFailureHandler(it) },
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
		failureHandler: FailureHandler = { defaultFailureHandler(it) },
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
		failureHandler: FailureHandler = { defaultFailureHandler(it) },
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
		failureHandler: FailureHandler = { defaultFailureHandler(it) },
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
		failureHandler: FailureHandler = { defaultFailureHandler(it) },
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
		failureHandler: FailureHandler = { defaultFailureHandler(it) },
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
) : Exception("HTTP Failure: $statusCode ${error.error}: ${error.displayMessage}")

typealias FailureHandler = suspend (HttpResponse) -> Throwable

suspend fun defaultFailureHandler(response: HttpResponse): Throwable {
	val error = response.body<HttpResponseError>()

	return HttpFailureException(
		statusCode = response.status,
		error = error
	)
}