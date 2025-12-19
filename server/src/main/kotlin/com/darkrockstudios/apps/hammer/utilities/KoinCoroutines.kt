package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_MAIN
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatformTools
import kotlin.coroutines.CoroutineContext

inline fun KoinComponent.injectDefaultDispatcher(
	mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
): Lazy<CoroutineContext> =
	lazy(mode) {
		get(qualifier = named(DISPATCHER_DEFAULT))
	}

inline fun KoinComponent.injectIoDispatcher(
	mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
): Lazy<CoroutineContext> =
	lazy(mode) {
		get(qualifier = named(DISPATCHER_IO))
	}

inline fun KoinComponent.injectMainDispatcher(
	mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
): Lazy<CoroutineContext> =
	lazy(mode) {
		get(qualifier = named(DISPATCHER_MAIN))
	}