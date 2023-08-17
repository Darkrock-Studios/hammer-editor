package com.darkrockstudios.apps.hammer

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class ServerConfig(
	val host: String = "localhost",
	val port: Int = 8080,
	val sslPort: Int = 443,
	val serverMessage: String = DEFAULT_MESSAGE,
	val contact: String? = null,
	val sslCert: SslCertConfig? = null,
	val defaultLocale: String = Locale.ENGLISH.toLanguageTag(),
) {
	fun getDefaultLocale(): Locale = Locale.Builder().setLanguageTag(defaultLocale).build()
}

@Serializable
data class SslCertConfig(
	val path: String,
	val storePassword: String,
	val keyAlias: String? = null,
	val keyPassword: String? = null,
	val forceHttps: Boolean = true
)

private val DEFAULT_MESSAGE = "Welcome to this instance of Hammer! Happy syncing!"
