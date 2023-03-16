package com.darkrockstudios.apps.hammer.common.dependencyinjection

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.compression.*

actual fun getHttpPlatformEngine(): HttpClientEngineFactory<*> = Java
actual fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installCompression() {
	install(ContentEncoding) {
		gzip()
		deflate()
	}
}