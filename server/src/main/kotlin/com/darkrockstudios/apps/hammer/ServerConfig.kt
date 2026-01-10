package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.email.EmailProvider
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
	val host: String = "localhost",
	val port: Int = 8080,
	val sslPort: Int = 443,
	val sslCert: SslCertConfig? = null,
	val patreonEnabled: Boolean? = null,
	val emailProvider: EmailProvider? = null,
	val communityEnabled: Boolean = false,
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
