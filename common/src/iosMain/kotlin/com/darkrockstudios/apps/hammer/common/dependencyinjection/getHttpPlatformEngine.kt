package com.darkrockstudios.apps.hammer.common.dependencyinjection

import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*

actual fun getHttpPlatformEngine(): HttpClientEngineFactory<*> = Darwin