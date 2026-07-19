package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_SERVER_VERSION
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.content.CachingOptions
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.hsts.HSTS
import io.ktor.server.plugins.httpsredirect.HttpsRedirect
import io.ktor.server.routing.IgnoreTrailingSlash

fun Application.configureHTTP(config: ServerConfig) {
	val analyticsProvider = config.analytics.provider
	val analyticsScriptHosts = analyticsProvider?.scriptSrcHosts().orEmpty()
	val analyticsConnectHosts = analyticsProvider?.connectSrcHosts().orEmpty()
	val analyticsImgHosts = analyticsProvider?.imgSrcHosts().orEmpty()

	install(DefaultHeaders) {
		header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
		header(HEADER_SERVER_VERSION, BuildMetadata.APP_VERSION)

		// Prevent MIME-sniffing attacks
		header("X-Content-Type-Options", "nosniff")

		// Prevent clickjacking of login/admin pages
		header("X-Frame-Options", "SAMEORIGIN")

		// Control referrer information to protect privacy
		header("Referrer-Policy", "strict-origin-when-cross-origin")

		// Content Security Policy - relaxed for compatibility
		val scriptSrc = (listOf("'self'", "https://unpkg.com", "'unsafe-inline'", "'unsafe-eval'") + analyticsScriptHosts)
			.joinToString(" ") // HTMX + inline scripts + dynamic eval + analytics
		val connectSrc = (listOf("'self'") + analyticsConnectHosts)
			.joinToString(" ") // HTMX requests stay on same origin + analytics event endpoint
		val imgSrc = (listOf("'self'", "data:") + analyticsImgHosts)
			.joinToString(" ") // Local images + data URIs + analytics pixels
		val cspDirectives = listOf(
			"default-src 'self'",
			"script-src $scriptSrc",
			"style-src 'self' https://cdnjs.cloudflare.com https://fonts.googleapis.com 'unsafe-inline'", // Font Awesome + Google Fonts + inline styles
			"font-src 'self' https://cdnjs.cloudflare.com https://fonts.gstatic.com data:", // Custom fonts + Font Awesome + Google Fonts
			"img-src $imgSrc",
			"connect-src $connectSrc",
			"frame-ancestors 'self'" // Additional clickjacking protection
		).joinToString("; ")
		header("Content-Security-Policy", cspDirectives)
	}
	install(ConditionalHeaders)
	install(CachingHeaders) {
		options { _, content -> staticAssetCaching(content.contentType) }
	}
	install(IgnoreTrailingSlash)
	install(Compression) {
		gzip {
			priority = 1.0
			minimumSize(1024)
		}
		deflate {
			priority = 10.0
			minimumSize(1024) // condition
		}
	}

	install(ApiProtocolEnforcerPlugin)

	if (config.sslCert?.forceHttps == true) {
		install(HttpsRedirect) {
			sslPort = config.sslPort
		}
		install(HSTS) {
			maxAgeInSeconds = 31536000
			includeSubDomains = true
		}
	}
}

/**
 * Long-lived, publicly cacheable `Cache-Control` for static assets (served from `/assets`), so
 * browsers can skip re-fetching CSS/JS/images/fonts on every navigation. ETags (via
 * ConditionalHeaders) still catch changes on revalidation once the max-age lapses. Non-asset
 * responses — HTML pages, XML sitemaps — get no caching header so they stay fresh and never
 * leak session-varying content into a shared cache.
 */
internal fun staticAssetCaching(contentType: ContentType?): CachingOptions? {
	val type = contentType?.withoutParameters() ?: return null
	return when {
		type.match(ContentType.Text.CSS) ||
			type.match(ContentType.Application.JavaScript) ||
			type.match(ContentType("text", "javascript")) ->
			CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 86_400, visibility = CacheControl.Visibility.Public))

		type.contentType == "image" || type.contentType == "font" ->
			CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 604_800, visibility = CacheControl.Visibility.Public))

		else -> null
	}
}
