package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_SERVER_VERSION
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.httpsredirect.*
import io.ktor.server.routing.*

fun Application.configureHTTP(config: ServerConfig) {
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
		val cspDirectives = listOf(
			"default-src 'self'",
			"script-src 'self' https://unpkg.com 'unsafe-inline' 'unsafe-eval'", // HTMX + inline scripts + dynamic eval
			"style-src 'self' https://cdnjs.cloudflare.com https://fonts.googleapis.com 'unsafe-inline'", // Font Awesome + Google Fonts + inline styles
			"font-src 'self' https://cdnjs.cloudflare.com https://fonts.gstatic.com data:", // Custom fonts + Font Awesome + Google Fonts
			"img-src 'self' data:", // Local images + data URIs
			"connect-src 'self'", // HTMX requests stay on same origin
			"frame-ancestors 'self'" // Additional clickjacking protection
		).joinToString("; ")
		header("Content-Security-Policy", cspDirectives)

		// Enforce HTTPS when SSL is configured
		if (config.sslCert != null) {
			header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
		}
	}
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
	}
}
