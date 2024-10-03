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
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
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
		Dispatchers.setMain(StandardTestDispatcher(scope.testScheduler))
	}

	@AfterEach
	open fun tearDown() {
		scope.cancel()
		stopKoin()
	}

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