package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.analytics.AnalyticsProvider
import com.darkrockstudios.apps.hammer.analytics.AnalyticsProviderFactory
import com.darkrockstudios.apps.hammer.email.EmailProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.net.URI
import java.net.URISyntaxException

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
	val sslPort: Int = 443,
	/**
	 * The externally visible base URL (e.g. "https://hammer.example.com"), used for
	 * links placed in emails. Set this when running behind a reverse proxy; otherwise
	 * links are derived from each request's Host header.
	 */
	val publicUrl: String? = null,
	val sslCert: SslCertConfig? = null,
	val patreonEnabled: Boolean? = null,
	/**
	 * Path to a plaintext file whose contents are presented as a Terms of Service that
	 * users must accept before an account is created. Null/absent disables the requirement.
	 */
	val termsOfService: String? = null,
	val emailProvider: String? = null,
	val communityEnabled: Boolean = false,
	val storage: StorageConfig = StorageConfig(),
	val analytics: AnalyticsConfig = AnalyticsConfig(),
	val encryption: EncryptionConfig = EncryptionConfig(),
	val secret: SecretConfig = SecretConfig(),
) {
	@Transient
	val emailProviderType: EmailProvider? = emailProvider?.let { provider ->
		EmailProvider.entries.find { it.name.equals(provider, ignoreCase = true) }
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
		// connect-src entries must be bare origins: a path/query/fragment would be emitted
		// verbatim into the CSP header and silently narrow what the browser allows.
		connectSrc.forEach { requireHttpUrl(it, "analytics.umami.connectSrc entry", bareOrigin = true) }
	}

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
