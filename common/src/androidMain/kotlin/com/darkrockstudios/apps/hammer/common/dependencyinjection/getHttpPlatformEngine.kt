package com.darkrockstudios.apps.hammer.common.dependencyinjection

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.compression.*

actual fun getHttpPlatformEngine(): HttpClientEngineFactory<*> = OkHttp
actual fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installCompression() {
	install(ContentEncoding) {
		gzip()
		deflate()
	}
}