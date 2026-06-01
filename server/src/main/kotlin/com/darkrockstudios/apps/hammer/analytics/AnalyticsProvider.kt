package com.darkrockstudios.apps.hammer.analytics

import com.darkrockstudios.apps.hammer.AnalyticsConfig
import com.darkrockstudios.apps.hammer.AnalyticsProviderType
import com.darkrockstudios.apps.hammer.UmamiConfig
import java.net.URI

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

internal class UmamiAnalyticsProvider(private val config: UmamiConfig) : AnalyticsProvider {
	override fun headSnippet(): String =
		"""<script defer src="${escapeAttr(config.scriptUrl)}" data-website-id="${escapeAttr(config.websiteId)}"></script>"""

	override fun scriptSrcHosts(): List<String> = listOf(originOf(config.scriptUrl))

	// Umami posts events to <origin>/api/send, i.e. the same origin the script is served from.
	override fun connectSrcHosts(): List<String> = listOf(originOf(config.scriptUrl))
}

object AnalyticsProviderFactory {
	/** Returns the active provider, or null when analytics is disabled/unconfigured. */
	fun create(config: AnalyticsConfig): AnalyticsProvider? = when (config.type) {
		AnalyticsProviderType.NONE -> null
		AnalyticsProviderType.UMAMI -> config.umami?.let { UmamiAnalyticsProvider(it) }
	}
}

/** Extracts the CSP-source origin (scheme://host[:port], no path) from a URL. */
internal fun originOf(url: String): String {
	val uri = URI(url)
	val scheme = uri.scheme ?: return url
	val host = uri.host ?: return url
	return if (uri.port != -1) "$scheme://$host:${uri.port}" else "$scheme://$host"
}

/** Minimal HTML-attribute escaping. Values are admin-controlled but rendered unescaped. */
internal fun escapeAttr(value: String): String =
	value.replace("&", "&amp;")
		.replace("\"", "&quot;")
		.replace("<", "&lt;")
		.replace(">", "&gt;")
