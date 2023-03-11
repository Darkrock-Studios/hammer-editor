package com.darkrockstudios.apps.hammer.common.dependencyinjection

import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.OkHttp

actual fun getHttpPlatformEngine(): HttpClientEngineFactory<*> = OkHttp