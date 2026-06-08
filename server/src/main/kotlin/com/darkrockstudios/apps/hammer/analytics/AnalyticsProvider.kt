package com.darkrockstudios.apps.hammer.analytics

import com.darkrockstudios.apps.hammer.AnalyticsConfig
import com.darkrockstudios.apps.hammer.AnalyticsProviderType
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
	/** Raw HTML injected into `<head>`, rendered via Mustache triple-braces. */
	fun headSnippet(): String

	/** Origins (scheme://host[:port], no path) to add to the CSP `script-src`. */
	fun scriptSrcHosts(): List<String>

	/** Origins to add to the CSP `connect-src` (where the provider POSTs events). */
	fun connectSrcHosts(): List<String>
}

internal class UmamiAnalyticsProvider(config: UmamiConfig) : AnalyticsProvider {
	// Computed once at construction: the provider is built once per config load, not per request.
	private val origin = originOf(config.scriptUrl)
	private val configuredConnectSrc = config.connectSrc
	private val snippet =
		"""<script defer src="${escapeAttr(config.scriptUrl)}" data-website-id="${escapeAttr(config.websiteId)}"></script>"""

	override fun headSnippet(): String = snippet

	override fun scriptSrcHosts(): List<String> = listOf(origin)

	// An explicit config override always wins. Otherwise: self-hosted Umami posts events to
	// <script-origin>/api/send, but Umami Cloud's script POSTs to separate gateway origins
	// baked into cloud.umami.is/script.js.
	override fun connectSrcHosts(): List<String> =
		configuredConnectSrc.ifEmpty {
			if (origin == UMAMI_CLOUD_ORIGIN) UMAMI_CLOUD_EVENT_ORIGINS else listOf(origin)
		}
}

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
