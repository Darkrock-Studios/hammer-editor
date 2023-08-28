package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.util.StrRes
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.get
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

@Composable
inline fun rememberStrRes() = remember<StrRes> { get(clazz = StrRes::class.java) }

@Composable
inline fun <reified T> rememberKoinInject(
	clazz: Class<*> = T::class.java,
	qualifier: Qualifier? = null,
	noinline parameters: ParametersDefinition? = null
): T {
	return remember<T> {
		return@remember get<T>(clazz, qualifier, parameters)
	}
}