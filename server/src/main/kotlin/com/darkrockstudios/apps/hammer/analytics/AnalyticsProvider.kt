package com.darkrockstudios.apps.hammer.analytics

import com.darkrockstudios.apps.hammer.AnalyticsConfig
import com.darkrockstudios.apps.hammer.AnalyticsProviderType
import com.darkrockstudios.apps.hammer.GoogleConfig
import com.darkrockstudios.apps.hammer.UmamiConfig
import java.net.URI
import java.net.URISyntaxException

/**
 * A configured web-analytics integration for the server's web frontend.
 *
 * Implementations are pure (no I/O): they turn admin-provided config into the
 * HTML to inject and the CSP origins that injection requires. Adding a new
 * provider is a new [AnalyticsProviderType] entry, a new config block, a new
 * implementation here, and one branch in [AnalyticsProviderFactory.create].
 */
interface AnalyticsProvider {
	/**
	 * Raw HTML injected into `<head>`, rendered via Mustache triple-braces.
	 *
	 * Must contain no inline script body — the CSP has no `'unsafe-inline'` to allow one. Vendor
	 * bootstraps that would otherwise be inline live in `assets/js/analytics.js`, driven by
	 * [clientConfig].
	 */
	fun headSnippet(): String

	/**
	 * Settings for `assets/js/analytics.js`, emitted as `data-*` attributes on its script tag.
	 *
	 * That script bootstraps the vendor API, defines `window.hammerTrack(name, data)` in this
	 * provider's terms, and forwards clicks on `[data-track-event]` elements to it.
	 */
	fun clientConfig(): Map<String, String>

	/** Origins (scheme://host[:port], no path) to add to the CSP `script-src`. */
	fun scriptSrcHosts(): List<String>

	/** Origins to add to the CSP `connect-src` (where the provider POSTs events). */
	fun connectSrcHosts(): List<String>

	/** Origins to add to the CSP `img-src` (where the provider loads tracking pixels). */
	fun imgSrcHosts(): List<String>
}

internal class UmamiAnalyticsProvider(config: UmamiConfig) : AnalyticsProvider {
	// Computed once at construction: the provider is built once per config load, not per request.
	private val origin = originOf(config.scriptUrl)
	private val configuredConnectSrc = config.connectSrc
	private val snippet =
		"""<script defer src="${escapeAttr(config.scriptUrl)}" data-website-id="${escapeAttr(config.websiteId)}"></script>"""

	override fun headSnippet(): String = snippet

	override fun clientConfig(): Map<String, String> = mapOf("provider" to "umami")

	override fun scriptSrcHosts(): List<String> = listOf(origin)

	// An explicit config override always wins. Otherwise: self-hosted Umami posts events to
	// <script-origin>/api/send, but Umami Cloud's script POSTs to separate gateway origins
	// baked into cloud.umami.is/script.js.
	override fun connectSrcHosts(): List<String> =
		configuredConnectSrc.ifEmpty {
			if (origin == UMAMI_CLOUD_ORIGIN) UMAMI_CLOUD_EVENT_ORIGINS else listOf(origin)
		}

	override fun imgSrcHosts(): List<String> = emptyList()
}

internal class GoogleAnalyticsProvider(config: GoogleConfig) : AnalyticsProvider {
	// Validated to ^G-[A-Za-z0-9]+$, so it is safe to interpolate into the attribute.
	private val id = escapeAttr(config.measurementId)
	private val measurementId = config.measurementId
	private val snippet = """<script async src="https://www.googletagmanager.com/gtag/js?id=$id"></script>"""

	override fun headSnippet(): String = snippet

	// The gtag bootstrap that Google documents as an inline script lives in analytics.js instead.
	override fun clientConfig(): Map<String, String> =
		mapOf("provider" to "google", "measurement-id" to measurementId)

	override fun scriptSrcHosts(): List<String> = GOOGLE_SCRIPT_HOSTS

	override fun connectSrcHosts(): List<String> = GOOGLE_CONNECT_HOSTS

	override fun imgSrcHosts(): List<String> = GOOGLE_IMG_HOSTS
}

// CSP origins for gtag.js, per Google's documented Content-Security-Policy guidance. GA4 beacons
// to *.google-analytics.com / *.analytics.google.com and falls back to pixel loads on img-src.
private val GOOGLE_SCRIPT_HOSTS = listOf("https://*.googletagmanager.com")
private val GOOGLE_CONNECT_HOSTS = listOf(
	"https://*.google-analytics.com",
	"https://*.analytics.google.com",
	"https://*.googletagmanager.com",
)
private val GOOGLE_IMG_HOSTS = listOf(
	"https://*.google-analytics.com",
	"https://*.googletagmanager.com",
)

private const val UMAMI_CLOUD_ORIGIN = "https://cloud.umami.is"

// cloud.umami.is/script.js POSTs events to a gateway origin that Umami has moved several times
// (and varies by region). All known hosts are allowed so cloud tracking keeps working across
// regions and cached script versions. If Umami moves it again, set analytics.umami.connectSrc
// in config to patch this without a code release.
private val UMAMI_CLOUD_EVENT_ORIGINS = listOf(
	"https://gateway.umami.is",
	"https://eu.umami.is",
	"https://api-gateway.umami.dev",
	"https://api-gateway-eu.umami.dev",
)

object AnalyticsProviderFactory {
	/** Returns the active provider, or null when analytics is disabled/unconfigured. */
	fun create(config: AnalyticsConfig): AnalyticsProvider? = when (config.type) {
		AnalyticsProviderType.NONE -> null
		AnalyticsProviderType.UMAMI -> config.umami?.let { UmamiAnalyticsProvider(it) }
		AnalyticsProviderType.GOOGLE -> config.google?.let { GoogleAnalyticsProvider(it) }
	}
}

/**
 * Extracts the CSP-source origin (scheme://host[:port], no path) from a URL.
 *
 * Never throws: a malformed URL is rejected upfront by [UmamiConfig.validate], so this
 * falls back to returning the raw string only as defense-in-depth.
 */
internal fun originOf(url: String): String {
	return try {
		val uri = URI(url)
		val scheme = uri.scheme ?: return url
		val host = uri.host ?: return url
		if (uri.port != -1) "$scheme://$host:${uri.port}" else "$scheme://$host"
	} catch (_: URISyntaxException) {
		url
	}
}

/** Minimal HTML-attribute escaping. Values are admin-controlled but rendered unescaped. */
internal fun escapeAttr(value: String): String =
	value.replace("&", "&amp;")
		.replace("\"", "&quot;")
		.replace("<", "&lt;")
		.replace(">", "&gt;")
