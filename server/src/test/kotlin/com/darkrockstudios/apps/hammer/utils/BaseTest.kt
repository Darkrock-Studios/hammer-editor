package com.darkrockstudios.apps.hammer.utils

import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_MAIN
import io.ktor.server.application.Application
import io.ktor.server.application.install
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
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseTest : KoinTest {

	val scope = TestScope()

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

	/**
	 * This is used for tests that ARE inside of a test Ktor application
	 */
	fun setupDispatchersFromKoin() {
		mainTestDispatcher = get<CoroutineContext>(named(DISPATCHER_MAIN)) as TestDispatcher
		ioTestDispatcher = get<CoroutineContext>(named(DISPATCHER_IO)) as TestDispatcher
		defaultTestDispatcher = get<CoroutineContext>(named(DISPATCHER_DEFAULT)) as TestDispatcher
	}

	/**
	 * Use this for tests that aren't inside of a test Ktor application
	 */
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

fun Application.setupKtorTestKoin(baseTest: BaseTest, vararg modules: Module) {
	install(Koin) {
		slf4jLogger()

		modules(
			module {
				single<CoroutineContext>(named(DISPATCHER_DEFAULT)) {
					StandardTestDispatcher(
						baseTest.scope.testScheduler,
						name = "Default dispatcher"
					)
				}
				single<CoroutineContext>(named(DISPATCHER_IO)) {
					StandardTestDispatcher(
						baseTest.scope.testScheduler,
						name = "IO dispatcher"
					)
				}
				single<CoroutineContext>(named(DISPATCHER_MAIN)) {
					StandardTestDispatcher(
						baseTest.scope.testScheduler,
						name = "Main dispatcher"
					)
				}
			},
			*modules
		)
	}
	baseTest.setupDispatchersFromKoin()
}
