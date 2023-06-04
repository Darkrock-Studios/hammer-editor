package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule = module {
	singleOf(::NetworkConnectivity)
}