package com.darkrockstudios.apps.hammer.services.languagetool

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.services.PerUserRateLimiter
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import kotlinx.io.readByteArray
import org.slf4j.LoggerFactory
import kotlin.time.Duration

/** Forwards checks to the admin's LanguageTool server. Never logs the text it carries. */
class LanguageToolProxy(
	private val configRepository: ConfigRepository,
	private val defaults: LanguageToolDefaults,
	private val rateLimiter: PerUserRateLimiter,
	private val httpClient: HttpClient = createUpstreamClient(),
) {
	private val log = LoggerFactory.getLogger(LanguageToolProxy::class.java)

	sealed interface Result {
		data class Reply(val status: HttpStatusCode, val contentType: ContentType?, val body: ByteArray) : Result
		data class Failed(val reason: String) : Result
	}

	/** Every write goes through [saveConfig], so the cache never goes stale on a single server. */
	@Volatile
	private var cached: LanguageToolConfig? = null

	suspend fun config(): LanguageToolConfig = cached ?: load().also { cached = it }

	private suspend fun load(): LanguageToolConfig =
		(configRepository.getStored(AdminServerConfig.LANGUAGETOOL_CONFIG) ?: defaults.config()).withinBounds()

	suspend fun saveConfig(config: LanguageToolConfig) {
		configRepository.set(AdminServerConfig.LANGUAGETOOL_CONFIG, config)
		cached = config.withinBounds()
	}

	/** Null when [userId] may send another request; otherwise how long until they can. */
	fun acquire(userId: Long, config: LanguageToolConfig): Duration? =
		rateLimiter.acquire(userId, config.requestsPerMinute)

	suspend fun check(config: LanguageToolConfig, form: Parameters): Result = upstream("check") {
		httpClient.submitForm("${config.baseUrl}/v2/check", formParameters = allowedFields(form))
	}

	suspend fun languages(config: LanguageToolConfig): Result = upstream("languages") {
		httpClient.get("${config.baseUrl}/v2/languages")
	}

	private suspend fun upstream(what: String, send: suspend () -> HttpResponse): Result = try {
		val response = send()
		val body = response.bodyAsChannel().readBuffer(MAX_REPLY_BYTES + 1).readByteArray()
		if (body.size > MAX_REPLY_BYTES) {
			log.warn("LanguageTool $what reply over $MAX_REPLY_BYTES bytes")
			Result.Failed("reply too large")
		} else {
			Result.Reply(response.status, response.contentType(), body)
		}
	} catch (e: IOException) {
		failed(what, e)
	} catch (e: UnresolvedAddressException) {
		failed(what, e)
	}

	private fun failed(what: String, e: Throwable): Result.Failed {
		log.warn("LanguageTool $what failed: ${e::class.simpleName}: ${e.message}")
		return Result.Failed(e.message ?: e::class.simpleName.orEmpty())
	}

	companion object {
		const val MAX_REPLY_BYTES = 8L * 1024 * 1024

		/** LanguageTool's check options. Anything else, credentials included, stays here. */
		val FORWARDED_FIELDS = setOf(
			"text", "data", "language", "motherTongue", "preferredVariants",
			"enabledRules", "disabledRules", "enabledCategories", "disabledCategories",
			"enabledOnly", "level",
		)

		fun allowedFields(form: Parameters): Parameters = Parameters.build {
			form.entries()
				.filter { (name, _) -> name in FORWARDED_FIELDS }
				.forEach { (name, values) -> appendAll(name, values) }
		}

		private fun createUpstreamClient() = HttpClient(Java) {
			expectSuccess = false
			followRedirects = false
			install(HttpTimeout) {
				connectTimeoutMillis = 5_000
				requestTimeoutMillis = 30_000
			}
		}
	}
}
