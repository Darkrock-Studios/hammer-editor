package com.darkrockstudios.apps.hammer.wear

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

/**
 * Starts Koin with the dispatchers every [com.darkrockstudios.apps.hammer.common.components.ComponentBase]
 * injects, all driven by one scheduler, and provides a real Decompose context to build components on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class WearTestBase {

	protected val scheduler = TestCoroutineScheduler()
	protected val dispatcher = StandardTestDispatcher(scheduler)

	protected lateinit var lifecycle: LifecycleRegistry
	protected lateinit var componentContext: DefaultComponentContext

	@BeforeEach
	open fun setUp() {
		Dispatchers.setMain(dispatcher)
		startKoin {
			modules(
				module {
					single<CoroutineContext>(named(DISPATCHER_MAIN)) { dispatcher }
					single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { dispatcher }
					single<CoroutineContext>(named(DISPATCHER_IO)) { dispatcher }
				}
			)
		}
		lifecycle = LifecycleRegistry()
		componentContext = DefaultComponentContext(lifecycle = lifecycle)
	}

	@AfterEach
	open fun tearDown() {
		if (lifecycle.state != Lifecycle.State.DESTROYED) lifecycle.destroy()
		stopKoin()
		Dispatchers.resetMain()
	}

	protected fun resumeLifecycle() = lifecycle.resume()
}

class FakeStrRes : StrRes {
	override suspend fun get(str: StringResource) = "text"
	override suspend fun get(str: StringResource, vararg args: Any) = "text"
}
