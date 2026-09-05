package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.compression.*
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/**
 * OkHttp, not the `java` engine: `java.net.http.HttpClient`'s constructor opens an NIO
 * [java.nio.channels.Selector], whose Windows wakeup pipe is an AF_UNIX socket that the MSIX
 * sandbox rejects with EINVAL, so every Store build hard-crashes on the first network call
 * (JDK-8312215). OkHttp uses blocking socket IO and survives the sandbox.
 */
actual fun getHttpPlatformEngine(): HttpClientEngineFactory<*> =
	if (getInDevelopmentMode()) DevLoopbackTrustOkHttp else OkHttp

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
 * when [getInDevelopmentMode] is true; release builds always use plain [OkHttp].
 */
private object DevLoopbackTrustOkHttp : HttpClientEngineFactory<OkHttpConfig> {
	override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine =
		OkHttp.create {
			block()
			val trustManager = loopbackTrustManager()
			val defaultVerifier = OkHttpClient.Builder().build().hostnameVerifier
			config {
				sslSocketFactory(loopbackTrustingSslContext(trustManager).socketFactory, trustManager)
				// OkHttp verifies the hostname itself rather than through the trust manager's
				// endpoint identification, so loopback has to be waived here too.
				hostnameVerifier { hostname, session ->
					isLoopbackHost(hostname) || defaultVerifier.verify(hostname, session)
				}
			}
		}
}

private fun loopbackTrustManager(): X509ExtendedTrustManager {
	val default = platformTrustManager()
	return object : X509ExtendedTrustManager() {
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
}

private fun loopbackTrustingSslContext(trustManager: X509ExtendedTrustManager): SSLContext =
	SSLContext.getInstance("TLSv1.3").apply {
		init(null, arrayOf(trustManager), SecureRandom())
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
