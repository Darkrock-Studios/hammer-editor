package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.compression.*
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

actual fun getHttpPlatformEngine(): HttpClientEngineFactory<*> =
	if (getInDevelopmentMode()) DevLoopbackTrustJava else Java

actual fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installCompression() {
	install(ContentEncoding) {
		gzip()
		deflate()
	}
}

/**
 * Dev-only engine that accepts a self-signed certificate ONLY from a loopback peer, so the desktop
 * client can reach a `--dev` server's auto-generated cert on localhost. Connections to any
 * non-loopback host still go through the platform's full certificate and hostname validation, so a
 * `--dev` build pointed at a real remote server is not exposed to a man-in-the-middle. Selected only
 * when [getInDevelopmentMode] is true; release builds always use plain [Java].
 */
private object DevLoopbackTrustJava : HttpClientEngineFactory<JavaHttpConfig> {
	override fun create(block: JavaHttpConfig.() -> Unit): HttpClientEngine =
		Java.create {
			block()
			config {
				sslContext(loopbackTrustingSslContext())
			}
		}
}

private fun loopbackTrustingSslContext(): SSLContext {
	val default = platformTrustManager()
	val trustManager = object : X509ExtendedTrustManager() {
		override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
			if (socket?.inetAddress?.isLoopbackAddress == true) return
			default.checkServerTrusted(chain, authType, socket)
		}

		override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
			if (isLoopbackHost(engine?.peerHost)) return
			default.checkServerTrusted(chain, authType, engine)
		}

		override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
			// No peer host available in this overload; fall back to real validation.
			default.checkServerTrusted(chain, authType)
		}

		override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
			default.checkClientTrusted(chain, authType, socket)

		override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
			default.checkClientTrusted(chain, authType, engine)

		override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
			default.checkClientTrusted(chain, authType)

		override fun getAcceptedIssuers(): Array<X509Certificate> = default.acceptedIssuers
	}
	return SSLContext.getInstance("TLS").apply {
		init(null, arrayOf(trustManager), SecureRandom())
	}
}

private fun isLoopbackHost(host: String?): Boolean {
	if (host.isNullOrBlank()) return false
	return runCatching { InetAddress.getByName(host).isLoopbackAddress }.getOrDefault(false)
}

private fun platformTrustManager(): X509ExtendedTrustManager {
	val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
	factory.init(null as KeyStore?)
	return factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().first()
}
