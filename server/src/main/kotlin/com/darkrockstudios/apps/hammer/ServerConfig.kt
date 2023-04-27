package com.darkrockstudios.apps.hammer

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
	val host: String = "localhost",
	val port: Int = 8080,
	val serverMessage: String = DEFAULT_MESSAGE,
	val contact: String? = null,
	val sslCert: SslCertConfig? = null,
)

@Serializable
data class SslCertConfig(
	val path: String,
	val storePassword: String,
	val keyAlias: String? = null,
	val keyPassword: String? = null,
	val forceHttps: Boolean = true
)

private val DEFAULT_MESSAGE = "Welcome to this instance of Hammer! Happy syncing!"