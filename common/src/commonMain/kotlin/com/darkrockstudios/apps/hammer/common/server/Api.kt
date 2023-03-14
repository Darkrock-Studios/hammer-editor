package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withContext
import okio.IOException
import org.koin.core.component.KoinComponent

abstract class Api(
    protected val httpClient: HttpClient,
    private val globalSettingsRepository: GlobalSettingsRepository
) : KoinComponent {
    private val baseUrl: String?
        get() = globalSettingsRepository.serverSettings?.url

    private val ioDispatcher by injectIoDispatcher()

    private suspend fun <T> makeRequest(
        path: String,
        builder: HttpRequestBuilder.() -> Unit = {},
        execute: suspend (
            urlString: String,
            block: HttpRequestBuilder.() -> Unit
        ) -> HttpResponse,
        parse: suspend (HttpResponse) -> T
    ): Result<T> = withContext(ioDispatcher) {
        val server = baseUrl ?: Result.failure<T>(IllegalStateException("Base URL not configured"))
        val url = "$server$path"
        return@withContext try {
            val response = execute(url, builder)

            if (response.status.isSuccess()) {
                val value = parse(response)
                Result.success(value)
            } else {
                val error = response.body<HttpResponseError>()
                Result.failure(
                    HttpFailureException(
                        statusCode = response.status,
                        error = error
                    )
                )
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    protected suspend fun post(path: String, builder: HttpRequestBuilder.() -> Unit = {}): Result<String> =
        makeRequest(
            path = path,
            builder = builder,
            execute = httpClient::post,
            parse = { it.bodyAsText() }
        )

    protected suspend fun <T> post(
        path: String,
        parse: suspend (HttpResponse) -> T,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): Result<T> =
        makeRequest(
            path = path,
            builder = builder,
            execute = httpClient::post,
            parse = parse
        )

    protected suspend fun get(
        path: String,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): Result<String> =
        makeRequest(
            path = path,
            builder = builder,
            execute = httpClient::get,
            parse = { it.bodyAsText() }
        )

    protected suspend fun <T> get(
        path: String,
        parse: suspend (HttpResponse) -> T,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): Result<T> =
        makeRequest(
            path = path,
            builder = builder,
            execute = httpClient::get,
            parse = parse
        )
}

class HttpFailureException(
    val statusCode: HttpStatusCode,
    val error: HttpResponseError
) : Exception("HTTP Failure: $statusCode ${error.error}: ${error.message}")