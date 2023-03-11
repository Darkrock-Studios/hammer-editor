package com.darkrockstudios.apps.hammer.common.dependencyinjection

import io.ktor.client.engine.*
import io.ktor.client.engine.java.*

actual fun getHttpPlatformEngine(): HttpClientEngineFactory<*> = Java
