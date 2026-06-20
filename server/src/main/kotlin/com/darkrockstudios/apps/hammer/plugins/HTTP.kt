package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_SERVER_VERSION
import io.ktor.server.application.Application
import io.ktor.server.application.install
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
		val cspDirectives = listOf(
			"default-src 'self'",
			"script-src $scriptSrc",
			"style-src 'self' https://cdnjs.cloudflare.com https://fonts.googleapis.com 'unsafe-inline'", // Font Awesome + Google Fonts + inline styles
			"font-src 'self' https://cdnjs.cloudflare.com https://fonts.gstatic.com data:", // Custom fonts + Font Awesome + Google Fonts
			"img-src 'self' data:", // Local images + data URIs
			"connect-src $connectSrc",
			"frame-ancestors 'self'" // Additional clickjacking protection
		).joinToString("; ")
		header("Content-Security-Policy", cspDirectives)
	}
	install(ConditionalHeaders)
	install(IgnoreTrailingSlash)
	install(Compression) {
		gzip {
			priority = 1.0
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
