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
import io.ktor.server.request.path
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

		// Isolate the browsing context group from any window we open or that opens us
		header("Cross-Origin-Opener-Policy", "same-origin")

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
		options { call, content ->
			staticAssetCaching(
				path = call.request.path(),
				contentType = content.contentType,
				versioned = call.request.queryParameters[ASSET_VERSION_PARAM] == AssetVersion.stamp,
			)
		}
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

/** Query parameter carrying [AssetVersion.stamp] on asset URLs the templates emit. */
internal const val ASSET_VERSION_PARAM = "v"

/**
 * Publicly cacheable `Cache-Control`, scoped by path so only content meant to be cached is:
 *
 * - Dynamic OG share cards under `/og/` cache for 30 days — the same window the disk cache prunes
 *   on — since a card's URL is keyed to a stable subject.
 * - Static files under `/assets` cache by type (CSS/JS one day, images/fonts a week), or for a year
 *   when [versioned] — the URL then carries [AssetVersion.stamp], so changing an asset mints a new
 *   URL rather than needing the old one to expire. References from inside a stylesheet carry no
 *   version and stay on the shorter windows, so nothing can be stuck stale.
 * - Everything else — HTML pages, XML sitemaps, and any dynamic image outside those paths — gets
 *   no caching header, so it stays fresh and never leaks session-varying content into a shared
 *   cache.
 *
 * ETags (via ConditionalHeaders) still catch changes on revalidation once a max-age lapses.
 */
internal fun staticAssetCaching(
	path: String,
	contentType: ContentType?,
	versioned: Boolean = false,
): CachingOptions? {
	val type = contentType?.withoutParameters()
	return when {
		path.startsWith("/og/") -> publicMaxAge(2_592_000)

		path.startsWith("/assets") && type != null -> when {
			type.match(ContentType.Text.CSS) ||
				type.match(ContentType.Application.JavaScript) ||
				type.match(ContentType("text", "javascript")) ->
				if (versioned) immutableForAYear() else publicMaxAge(86_400)

			type.contentType == "image" || type.contentType == "font" ->
				if (versioned) immutableForAYear() else publicMaxAge(604_800)

			else -> null
		}

		else -> null
	}
}

private fun publicMaxAge(seconds: Int) =
	CachingOptions(CacheControl.MaxAge(maxAgeSeconds = seconds, visibility = CacheControl.Visibility.Public))

private fun immutableForAYear() = CachingOptions(ImmutableCacheControl)

/** Ktor's [CacheControl.MaxAge] can't express `immutable`, which spares versioned assets a
 *  revalidation round trip on reload. */
private object ImmutableCacheControl : CacheControl(CacheControl.Visibility.Public) {
	private const val ONE_YEAR_SECONDS = 31_536_000

	override fun toString() = "max-age=$ONE_YEAR_SECONDS, public, immutable"
}
