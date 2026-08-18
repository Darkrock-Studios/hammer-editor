package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.analytics.AnalyticsProvider
import com.darkrockstudios.apps.hammer.analytics.AnalyticsProviderFactory
import com.darkrockstudios.apps.hammer.email.EmailProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Serializable
data class ServerConfig(
	val host: String = "localhost",
	/**
	 * Network interfaces the server binds to. Defaults to all IPv4 interfaces.
	 * Set to `["127.0.0.1", "::1"]` to accept loopback connections only, e.g.
	 * when running behind a reverse proxy on the same host. Distinct from [host],
	 * which is the public name shown to users.
	 */
	val bindHosts: List<String> = listOf("0.0.0.0"),
	val port: Int = 8080,
	val sslPort: Int = DEFAULT_SSL_PORT,
	/**
	 * The externally visible base URL (e.g. "https://hammer.example.com"), used for
	 * links placed in emails. Set this when running behind a reverse proxy; otherwise
	 * links are derived from each request's Host header.
	 */
	val publicUrl: String? = null,
	/**
	 * Trust the `X-Forwarded-*` headers for each request's client address and scheme. Only safe
	 * when the proxy is the only route in, since anything reaching the server directly can forge
	 * them. Assumes a single proxy: the address comes from the last `X-Forwarded-For` entry.
	 */
	val trustProxyForwarding: Boolean = false,
	/**
	 * IANA zone ID (e.g. "Europe/Paris") that server-rendered timestamps and log lines are stamped
	 * in. Absent, the `HAMMER_TIMEZONE` environment variable is used, then `TZ`, then the host's own
	 * zone. An unknown ID aborts startup.
	 */
	val timezone: String? = null,
	val additionalSitemaps: List<String> = emptyList(),
	/**
	 * Generate per-page social share images (OpenGraph) on the fly for author and story pages.
	 * Requires native font libraries for headless AWT text rendering — e.g. `fontconfig` and
	 * `libfreetype6` on Debian/Ubuntu. When off (the default), share links fall back to branded
	 * static cards, so the out-of-the-box setup needs nothing extra installed.
	 */
	val richLinkPreviews: Boolean = false,
	val sslCert: SslCertConfig? = null,
	val patreonEnabled: Boolean? = null,
	/**
	 * Path to a plaintext file whose contents are presented as a Terms of Service that
	 * users must accept before an account is created. Null/absent disables the requirement.
	 * A relative path is resolved against the config file's own directory, so `tos.txt` finds
	 * a file sitting next to `config.toml`. A configured path that can't be read aborts startup.
	 */
	val termsOfService: String? = null,
	/**
	 * Path to a plaintext file whose contents are published at `/privacy` and linked from the
	 * footer. Null/absent hides the page and the link. Resolution and startup validation mirror
	 * [termsOfService]: a relative path resolves against the config file's directory, and a
	 * configured path that can't be read aborts startup.
	 */
	val privacyPolicy: String? = null,
	val emailProvider: String? = null,
	val communityEnabled: Boolean = false,
	val extraLinks: List<ExtraLink> = emptyList(),
	val accountDeletion: AccountDeletionConfig = AccountDeletionConfig(),
	val storage: StorageConfig = StorageConfig(),
	val cache: CacheConfig = CacheConfig(),
	val analytics: AnalyticsConfig = AnalyticsConfig(),
	val encryption: EncryptionConfig = EncryptionConfig(),
	val secret: SecretConfig = SecretConfig(),
) {
	@Transient
	val emailProviderType: EmailProvider? = emailProvider?.let { provider ->
		EmailProvider.entries.find { it.name.equals(provider, ignoreCase = true) }
	}

	companion object {
		const val DEFAULT_SSL_PORT = 443
	}
}

@Serializable(with = LinkPlacement.Serializer::class)
enum class LinkPlacement(val serial: String) {
	HEADER("header"),
	FOOTER("footer"),
	BOTH("both");

	val inHeader: Boolean get() = this == HEADER || this == BOTH
	val inFooter: Boolean get() = this == FOOTER || this == BOTH

	object Serializer : CaseInsensitiveEnumSerializer<LinkPlacement>(
		"LinkPlacement", entries.toTypedArray(), { it.serial }
	)
}

/**
 * An operator-defined nav link appended to the header and/or footer, for pointing a deployment at
 * content that isn't part of Hammer itself. Each entry is a `[[extraLinks]]` block in `config.toml`:
 *
 * ```toml
 * [[extraLinks]]
 * url = "/blog"
 * title = "Blog"
 * translations = { de = "Blog", fr = "Blogue" }
 * icon = "fa-solid fa-blog"
 * placement = "header"
 * ```
 *
 * [translations] is an inline table so each entry stays self-contained; the sub-table form
 * (`[extraLinks.translations]`) binds to whichever `[[extraLinks]]` precedes it.
 */
@Serializable
data class ExtraLink(
	/** Site-relative (`/blog`) or an absolute http(s) URL. */
	val url: String,
	/** Label used when [translations] has no entry for the viewer's locale. */
	val title: String,
	/**
	 * Labels keyed by language tag, matched against the full tag (`pt-BR`) then the bare
	 * language (`pt`). Underscores are accepted, so a key copied from a bundle filename
	 * (`pt_BR`) works as well as the canonical `pt-BR`.
	 */
	val translations: Map<String, String> = emptyMap(),
	/** FontAwesome classes, e.g. `fa-solid fa-blog`. */
	val icon: String = "fa-solid fa-link",
	val placement: LinkPlacement = LinkPlacement.FOOTER,
) {
	val isExternal: Boolean
		get() = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

	fun title(locale: Locale): String {
		val exact = normalizeLanguageTag(locale.toLanguageTag())
		val language = normalizeLanguageTag(locale.language)
		return translations.entries.firstOrNull { normalizeLanguageTag(it.key) == exact }?.value
			?: translations.entries.firstOrNull { normalizeLanguageTag(it.key) == language }?.value
			?: title
	}

	fun validate() {
		require(title.isNotBlank()) { "extraLinks entry with url \"$url\" must have a non-blank title" }
		translations.forEach { (tag, label) ->
			require(label.isNotBlank()) { "extraLinks entry \"$title\" has a blank title for locale \"$tag\"" }
			// A key the JVM can't round-trip would never match a viewer's locale, so it would
			// silently render the fallback title forever.
			val normalized = normalizeLanguageTag(tag)
			require(Locale.forLanguageTag(normalized).toLanguageTag().lowercase() == normalized) {
				"extraLinks entry \"$title\" has an unusable locale key \"$tag\"; " +
					"use a language tag like \"de\" or \"pt-BR\""
			}
		}
		require(icon.isNotBlank()) { "extraLinks entry \"$title\" must have a non-blank icon" }

		if (isExternal) {
			requireHttpUrl(url, "extraLinks entry \"$title\" url")
		} else {
			// Browsers resolve both "//host" and "/\host" as protocol-relative, so either would
			// leave the site despite looking local. Any other scheme (javascript:, data:) has no
			// business in a nav href.
			require(url.startsWith("/") && url.getOrNull(1) !in setOf('/', '\\')) {
				"extraLinks entry \"$title\" url must be site-relative (start with \"/\") " +
					"or an absolute http(s) URL: $url"
			}
		}
	}
}

/** Language tags compare case-insensitively and treat `pt_BR` and `pt-BR` as the same key. */
private fun normalizeLanguageTag(tag: String): String = tag.replace('_', '-').lowercase()

/**
 * Requires [value] to be an absolute http(s) URL. With [bareOrigin], it must also carry no
 * path, query, or fragment — a CSP `connect-src` entry emits verbatim into the header, and
 * anything past the origin silently narrows what the browser allows.
 */
private fun requireHttpUrl(value: String, label: String, bareOrigin: Boolean = false) {
	val uri = try {
		URI(value)
	} catch (e: URISyntaxException) {
		throw IllegalArgumentException("$label is not a valid URL: $value", e)
	}
	require(uri.scheme?.lowercase() in setOf("http", "https") && uri.host != null) {
		"$label must be an absolute http(s) URL: $value"
	}
	if (bareOrigin) {
		require(uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null) {
			"$label must be a bare origin (scheme://host[:port], no path): $value"
		}
	}
}

@Serializable(with = AnalyticsProviderType.Serializer::class)
enum class AnalyticsProviderType(val serial: String) {
	NONE("none"),
	UMAMI("umami"),
	GOOGLE("google");

	object Serializer : CaseInsensitiveEnumSerializer<AnalyticsProviderType>(
		"AnalyticsProviderType", entries.toTypedArray(), { it.serial }
	)
}

@Serializable
data class AnalyticsConfig(
	val type: AnalyticsProviderType = AnalyticsProviderType.NONE,
	val umami: UmamiConfig? = null,
	val google: GoogleConfig? = null,
) {
	/** The active provider, resolved once when the config is loaded. Null when analytics is off. */
	@Transient
	val provider: AnalyticsProvider? = AnalyticsProviderFactory.create(this)

	fun validate() {
		when (type) {
			AnalyticsProviderType.NONE -> Unit
			AnalyticsProviderType.UMAMI -> {
				requireNotNull(umami) { "analytics.type=umami requires an [analytics.umami] config block" }
				umami.validate()
			}
			AnalyticsProviderType.GOOGLE -> {
				requireNotNull(google) { "analytics.type=google requires an [analytics.google] config block" }
				google.validate()
			}
		}
	}
}

@Serializable
data class UmamiConfig(
	/** The Umami "website ID" (UUID) for this site. */
	val websiteId: String,
	/** Defaults to Umami Cloud; override with https://<your-host>/script.js for self-hosted Umami. */
	val scriptUrl: String = "https://cloud.umami.is/script.js",
	/**
	 * Overrides the CSP `connect-src` event hosts. Umami Cloud's script POSTs events to a
	 * gateway origin that has changed several times (gateway.umami.is, api-gateway.umami.dev,
	 * …); set this to patch CSP from config when it changes again, without a code release.
	 * Each entry must be a bare origin (scheme://host[:port], no path). Empty = built-in defaults.
	 */
	val connectSrc: List<String> = emptyList(),
) {
	fun validate() {
		require(websiteId.isNotBlank()) { "analytics.umami.websiteId must not be blank" }
		require(scriptUrl.isNotBlank()) { "analytics.umami.scriptUrl must not be blank" }
		requireHttpUrl(scriptUrl, "analytics.umami.scriptUrl")
		connectSrc.forEach { requireHttpUrl(it, "analytics.umami.connectSrc entry", bareOrigin = true) }
	}
}

@Serializable
data class GoogleConfig(
	/** The GA4 Measurement ID, e.g. "G-XXXXXXXXXX", from the Google Analytics data stream. */
	val measurementId: String,
) {
	fun validate() {
		require(measurementId.isNotBlank()) { "analytics.google.measurementId must not be blank" }
		// Strict format keeps the id safe to interpolate verbatim into the inline gtag init script.
		require(MEASUREMENT_ID_REGEX.matches(measurementId)) {
			"analytics.google.measurementId must look like a GA4 id (G-XXXXXXXXXX): $measurementId"
		}
	}

	private companion object {
		val MEASUREMENT_ID_REGEX = Regex("^G-[A-Za-z0-9]+$")
	}
}

@Serializable(with = EncryptionMode.Serializer::class)
enum class EncryptionMode(val serial: String) {
	AES("aes"),
	NONE("none");

	object Serializer : CaseInsensitiveEnumSerializer<EncryptionMode>(
		"EncryptionMode", entries.toTypedArray(), { it.serial }
	)
}

/**
 * Selects the cipher used for newly written content. Reads dispatch per-row
 * regardless of this.
 *
 * `mode` is **unspecified** (null) by default — distinct from an explicit
 * `none`. Unspecified resolves to plaintext on a fresh server, but the boot
 * gate hard-stops a server that already holds encrypted data, forcing the
 * admin to choose `aes` or `none` deliberately. An explicit `none` is a
 * request to converge existing data to plaintext.
 */
@Serializable
data class EncryptionConfig(
	val mode: EncryptionMode? = null,
) {
	/** What new writes use; unspecified behaves as plaintext. */
	fun effectiveWriteMode(): EncryptionMode = mode ?: EncryptionMode.NONE
}

@Serializable(with = SecretProviderType.Serializer::class)
enum class SecretProviderType(val serial: String) {
	FILE("file"),
	ENV("env");

	object Serializer : CaseInsensitiveEnumSerializer<SecretProviderType>(
		"SecretProviderType", entries.toTypedArray(), { it.serial }
	)
}

/** Where the keyring document is read from. The keyring is never written at runtime. */
@Serializable
data class SecretConfig(
	val provider: SecretProviderType = SecretProviderType.FILE,
	/** Keyring JSON file path for the `file` provider. Defaults to hammer_data/server.keyring.json. */
	val file: String? = null,
	/** Environment variable holding the keyring JSON for the `env` provider. */
	val envVar: String = "HAMMER_KEYRING",
)

@Serializable(with = StorageMode.Serializer::class)
enum class StorageMode(val serial: String) {
	EMBEDDED("embedded"),
	REMOTE("remote");

	object Serializer : CaseInsensitiveEnumSerializer<StorageMode>(
		"StorageMode", entries.toTypedArray(), { it.serial }
	)
}

@Serializable
data class StorageConfig(
	val type: StorageMode = StorageMode.EMBEDDED,
	val embedded: EmbeddedPostgresConfig = EmbeddedPostgresConfig(),
	val remote: RemotePostgresConfig? = null,
) {
	fun validate() {
		if (type == StorageMode.REMOTE) {
			require(remote != null) { "storage.type=remote requires storage.remote config block" }
		}
	}
}

/**
 * Self-service account deletion. A deleted account is soft-deleted first: locked out of
 * login and sync, unpublished, pen name released, but its data is retained so an operator
 * can restore it. A daily job permanently deletes accounts soft-deleted longer than
 * [retentionDays]. The window is evaluated against each account's deletion time on every
 * job run, so lowering it also hard-deletes accounts already past the new window.
 */
@Serializable
data class AccountDeletionConfig(
	/** Days a soft-deleted account is retained before permanent deletion. */
	val retentionDays: Int = 30,
) {
	@Transient
	val retention: Duration = retentionDays.days

	fun validate() {
		require(retentionDays in 1..MAX_RETENTION_DAYS) {
			"accountDeletion.retentionDays must be between 1 and $MAX_RETENTION_DAYS, was $retentionDays"
		}
	}

	private companion object {
		const val MAX_RETENTION_DAYS = 3650
	}
}

/**
 * The regenerable disk caches: rendered story HTML and OpenGraph share cards. Nothing here is
 * durable data — losing the whole directory costs a re-render, so it belongs on whatever volume
 * has room for churn rather than alongside the database.
 */
@Serializable
data class CacheConfig(
	/**
	 * Where the caches live, one subdirectory per cache. Defaults to `cache/` under the server's
	 * data directory. Point it at a scratch volume (e.g. "/var/tmp/hammer-cache") to keep the churn
	 * off the data partition. A relative path is resolved against the config file's own directory.
	 */
	val directory: String? = null,
	/** Size bound for each cache, enforced by evicting least-recently-used entries. */
	val maxSizeMb: Long = 200,
) {
	@Transient
	val maxSizeBytes: Long = maxSizeMb * 1024 * 1024

	fun validate() {
		directory?.let { require(it.isNotBlank()) { "cache.directory must not be blank" } }
		// Upper bound as well as lower: a value given in bytes by mistake would overflow the
		// conversion to a negative cap, which only surfaces as a failure to build the cache.
		require(maxSizeMb in 1..MAX_SIZE_MB) {
			"cache.maxSizeMb must be between 1 and $MAX_SIZE_MB, was $maxSizeMb"
		}
	}

	private companion object {
		const val MAX_SIZE_MB = 1024L * 1024
	}
}

/** Configuration for the in-process Zonky embedded Postgres server. */
@Serializable
data class EmbeddedPostgresConfig(
	/** Pinned port — predictable for ops. Override in TOML if it collides. */
	val port: Int = 54329,
	/** Subdirectory under hammer_data/ to use as Postgres' data dir. */
	val dataDirName: String = "pgdata",
)

/** Connection details for an externally-managed PostgreSQL server. */
@Serializable
data class RemotePostgresConfig(
	val host: String,
	val port: Int = 5432,
	val database: String,
	val user: String,
	val password: String,
	val schema: String = "public",
	val poolSize: Int = 10,
	val useSsl: Boolean = true,
)

@Serializable
data class SslCertConfig(
	// Option 1: JKS/PKCS12 keystore file
	val path: String? = null,
	val storePassword: String? = null,
	val keyAlias: String? = null,
	val keyPassword: String? = null,

	// Option 2: PEM files (e.g., from Let's Encrypt)
	val certChainPath: String? = null,  // fullchain.pem
	val privateKeyPath: String? = null, // privkey.pem

	val forceHttps: Boolean = true
) {
	fun validate(): Boolean {
		val hasKeystore = path != null && storePassword != null
		val hasPem = certChainPath != null && privateKeyPath != null
		return hasKeystore || hasPem
	}

	fun usePem(): Boolean {
		return certChainPath != null && privateKeyPath != null
	}
}
