package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.email.EmailProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ServerConfig(
	val host: String = "localhost",
	val port: Int = 8080,
	val sslPort: Int = 443,
	val sslCert: SslCertConfig? = null,
	val patreonEnabled: Boolean? = null,
	val emailProvider: String? = null,
	val communityEnabled: Boolean = false,
	val storage: StorageConfig = StorageConfig(),
) {
	@Transient
	val emailProviderType: EmailProvider? = emailProvider?.let { provider ->
		EmailProvider.entries.find { it.name.equals(provider, ignoreCase = true) }
	}
}

@Serializable
enum class StorageMode { EMBEDDED, REMOTE }

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
