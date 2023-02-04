package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import kotlin.coroutines.CoroutineContext

fun getDefaultDispatcher(): CoroutineContext {
    return KoinJavaComponent.get(clazz = CoroutineContext::class.java, qualifier = named(DISPATCHER_DEFAULT))
}

fun getIoDispatcher(): CoroutineContext {
    return KoinJavaComponent.get(clazz = CoroutineContext::class.java, qualifier = named(DISPATCHER_IO))
}

fun getMainDispatcher(): CoroutineContext {
    return KoinJavaComponent.get(clazz = CoroutineContext::class.java, qualifier = named(DISPATCHER_MAIN))
}

@Composable
inline fun rememberDefaultDispatcher(): CoroutineContext = remember { getDefaultDispatcher() }

@Composable
inline fun rememberIoDispatcher(): CoroutineContext = remember { getIoDispatcher() }

@Composable
inline fun rememberMainDispatcher(): CoroutineContext = remember { getMainDispatcher() }