package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_MAIN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseTest : KoinTest {

	protected val scope = TestScope()

	lateinit var mainTestDispatcher: TestDispatcher
	lateinit var ioTestDispatcher: TestDispatcher
	lateinit var defaultTestDispatcher: TestDispatcher

	@Before
	open fun setup() {
		Dispatchers.setMain(StandardTestDispatcher(scope.testScheduler))
	}

	@After
	open fun tearDown() {
		scope.cancel()
		stopKoin()
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	fun setupKoin(vararg modules: Module) {
		val scheduler = scope.testScheduler
		GlobalContext.startKoin {
			modules(
				module {
					single<CoroutineContext>(named(DISPATCHER_DEFAULT)) {
						StandardTestDispatcher(
							scheduler,
							name = "Default dispatcher"
						)
					}
					single<CoroutineContext>(named(DISPATCHER_IO)) {
						StandardTestDispatcher(
							scheduler,
							name = "IO dispatcher"
						)
					}
					single<CoroutineContext>(named(DISPATCHER_MAIN)) {
						StandardTestDispatcher(
							scheduler,
							name = "Main dispatcher"
						)
					}
				},
				*modules
			)
		}

		mainTestDispatcher = get<CoroutineContext>(named(DISPATCHER_MAIN)) as TestDispatcher
		ioTestDispatcher = get<CoroutineContext>(named(DISPATCHER_IO)) as TestDispatcher
		defaultTestDispatcher = get<CoroutineContext>(named(DISPATCHER_DEFAULT)) as TestDispatcher
	}
}