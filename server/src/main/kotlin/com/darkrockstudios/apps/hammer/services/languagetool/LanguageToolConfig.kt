package com.darkrockstudios.apps.hammer.services.languagetool

import kotlinx.serialization.Serializable

@Serializable
data class LanguageToolConfig(
	/** The upstream LanguageTool server, such as `http://languagetool:8010`. */
	val url: String = "",
	val requestsPerMinute: Int = DEFAULT_REQUESTS_PER_MINUTE,
	val maxRequestBytes: Int = DEFAULT_MAX_REQUEST_BYTES,
) {
	val isConfigured: Boolean get() = url.isNotBlank()

	/** [url] without a trailing `/`, `/v2`, or `/v2/check`, so any of the forms an admin pastes works. */
	val baseUrl: String
		get() = url.trim().trimEnd('/').removeSuffix("/v2/check").removeSuffix("/v2").trimEnd('/')

	/** Limits raised to their floors, so a stored zero can't refuse every request. */
	fun withinBounds() = copy(
		requestsPerMinute = requestsPerMinute.coerceAtLeast(1),
		maxRequestBytes = maxRequestBytes.coerceAtLeast(MIN_MAX_REQUEST_BYTES),
	)

	companion object {
		/** An absolute http(s) address, or null when [url] isn't one. */
		fun validUrl(url: String): String? = url.trim().takeIf {
			runCatching { java.net.URI(it) }.getOrNull()
				?.let { uri -> uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() } == true
		}

		const val DEFAULT_REQUESTS_PER_MINUTE = 60
		const val DEFAULT_MAX_REQUEST_BYTES = 100_000
		const val MIN_MAX_REQUEST_BYTES = 1_000
	}
}

/** The config used until an admin saves one, with the upstream the deployment names, if any. */
class LanguageToolDefaults(private val url: String?) {
	fun config(): LanguageToolConfig = LanguageToolConfig(url = url?.trim().orEmpty())

	companion object {
		const val URL_ENV = "HAMMER_LANGUAGETOOL_URL"

		fun fromEnvironment() = LanguageToolDefaults(System.getenv(URL_ENV))
	}
}
