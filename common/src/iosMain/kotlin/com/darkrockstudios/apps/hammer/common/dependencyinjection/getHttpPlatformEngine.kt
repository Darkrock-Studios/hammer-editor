package com.darkrockstudios.apps.hammer.common.dependencyinjection

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.compression.*

actual fun getHttpPlatformEngine(): HttpClientEngineFactory<*> = Darwin
actual fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installCompression() {
	install(ContentEncoding) {
		gzip()
		deflate()
	}
}