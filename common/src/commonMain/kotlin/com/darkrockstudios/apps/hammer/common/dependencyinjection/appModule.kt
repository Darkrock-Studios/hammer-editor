package com.darkrockstudios.apps.hammer.common.dependencyinjection

import kotlinx.coroutines.CoroutineScope
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val APP_SCOPE = "app-scope"

fun appModule(appScope: CoroutineScope) = module {
	single(named(APP_SCOPE)) { appScope }
}