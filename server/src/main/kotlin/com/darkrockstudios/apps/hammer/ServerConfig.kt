package com.darkrockstudios.apps.hammer

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
	val host: String = "localhost",
	val port: Int = 8080,
	val sslPort: Int = 443,
	val sslCert: SslCertConfig? = null,
) {
}

@Serializable
data class SslCertConfig(
	val path: String,
	val storePassword: String,
	val keyAlias: String? = null,
	val keyPassword: String? = null,
	val forceHttps: Boolean = true
)
