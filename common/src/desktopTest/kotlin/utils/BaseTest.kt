package utils

import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
open class BaseTest : KoinTest {

	protected lateinit var scope: TestScope

	lateinit var mainTestDispatcher: TestDispatcher
	lateinit var ioTestDispatcher: TestDispatcher
	lateinit var defaultTestDispatcher: TestDispatcher

	@BeforeEach
	open fun setup() {
		scope = TestScope()
		mainTestDispatcher = StandardTestDispatcher(
			scope.testScheduler,
			name = "Main dispatcher"
		)
		Dispatchers.setMain(mainTestDispatcher)
	}

	@AfterEach
	open fun tearDown() {
		GlobalContext.stopKoin()
		scope.cancel()
		Dispatchers.resetMain()
	}

	fun setupKoin(vararg modules: Module) {
		val scheduler = scope.testScheduler

		GlobalContext.stopKoin()
		GlobalContext.startKoin {
			modules(
				module {
					single<CoroutineContext>(named(DISPATCHER_MAIN)) { mainTestDispatcher }
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
				},
				*modules
			)
		}

		ioTestDispatcher = get<CoroutineContext>(named(DISPATCHER_IO)) as TestDispatcher
		defaultTestDispatcher = get<CoroutineContext>(named(DISPATCHER_DEFAULT)) as TestDispatcher
	}

//	fun runKoinTest(vararg modules: Module, block: suspend TestScope.() -> Unit) {
//		setupKoin(*modules)
//		runTest {
//			block()
//			GlobalContext.stopKoin()
//		}
//	}
}