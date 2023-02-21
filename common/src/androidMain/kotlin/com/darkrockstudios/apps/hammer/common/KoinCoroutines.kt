package com.darkrockstudios.apps.hammer.common

import android.content.ComponentCallbacks
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import org.koin.android.ext.android.get
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatformTools
import kotlin.coroutines.CoroutineContext

inline fun ComponentCallbacks.injectDefaultDispatcher(
	mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
): Lazy<CoroutineContext> =
	lazy(mode) {
		get(qualifier = named(DISPATCHER_DEFAULT))
	}

inline fun ComponentCallbacks.injectIoDispatcher(
	mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
): Lazy<CoroutineContext> =
	lazy(mode) {
		get(qualifier = named(DISPATCHER_IO))
	}

inline fun ComponentCallbacks.injectMainDispatcher(
	mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
): Lazy<CoroutineContext> =
	lazy(mode) {
		get(qualifier = named(DISPATCHER_MAIN))
	}