package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.util.StrRes
import org.koin.compose.koinInject
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform
import kotlin.coroutines.CoroutineContext

fun getDefaultDispatcher(): CoroutineContext =
	KoinPlatform.getKoin().get(qualifier = named(DISPATCHER_DEFAULT))

fun getIoDispatcher(): CoroutineContext =
	KoinPlatform.getKoin().get(qualifier = named(DISPATCHER_IO))

fun getMainDispatcher(): CoroutineContext =
	KoinPlatform.getKoin().get(qualifier = named(DISPATCHER_MAIN))

@Composable
fun rememberDefaultDispatcher(): CoroutineContext = koinInject(qualifier = named(DISPATCHER_DEFAULT))

@Composable
fun rememberIoDispatcher(): CoroutineContext = koinInject(qualifier = named(DISPATCHER_IO))

@Composable
fun rememberMainDispatcher(): CoroutineContext = koinInject(qualifier = named(DISPATCHER_MAIN))

@Composable
fun rememberStrRes(): StrRes = koinInject()

@Composable
inline fun <reified T : Any> rememberKoinInject(
	qualifier: Qualifier? = null,
	noinline parameters: ParametersDefinition? = null
): T = if (parameters == null) {
	koinInject(qualifier = qualifier)
} else {
	koinInject(qualifier = qualifier, parameters = parameters)
}
