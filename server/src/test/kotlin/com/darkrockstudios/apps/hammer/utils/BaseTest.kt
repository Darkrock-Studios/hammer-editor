package com.darkrockstudios.apps.hammer.utils

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.monitoring.ErrorRepository
import com.darkrockstudios.apps.hammer.monitoring.MetricsRepository
import com.darkrockstudios.apps.hammer.monitoring.UserActivityCollector
import com.darkrockstudios.apps.hammer.monitoring.UserActivityRepository
import com.darkrockstudios.apps.hammer.monitoring.MonitoringState
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.plugins.LoginRateLimitConfig
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import io.ktor.server.application.*
import io.mockk.mockk
import kotlin.time.Clock
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

	@BeforeEach
	open fun setup() {
		Dispatchers.setMain(StandardTestDispatcher(scope.testScheduler))
	}

	@AfterEach
	open fun tearDown() {
		scope.cancel()
		GlobalContext.stopKoin()
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
		GlobalContext.stopKoin()
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
				single { ServerConfig() }
				// Effectively unlimited so login tests never trip the limiter.
				single { LoginRateLimitConfig(limit = 1_000_000, refillPeriodSeconds = 1) }
				// Monitoring beans: the web layer (frontend/admin pages + the
				// StatusPages error recorder) constructor-injects these, so every
				// test that boots the app via configureRouting needs them present.
				// They're inert stand-ins; no monitoring route is exercised here.
				single<Clock> { Clock.System }
				single { MonitoringState() }
				single<MetricsRepository> { mockk(relaxed = true) }
				single<ErrorRepository> { mockk(relaxed = true) }
				single<SecurityRepository> { mockk(relaxed = true) }
				single<UserActivityRepository> { mockk(relaxed = true) }
				single<UserActivityCollector> { mockk(relaxed = true) }
				single<SyncSessionManager<Long, ProjectsSynchronizationSession>>(named(PROJECTS_SYNC_MANAGER)) {
					mockk(relaxed = true)
				}
				single<SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>>(named(PROJECT_SYNC_MANAGER)) {
					mockk(relaxed = true)
				}
			},
			*modules
		)
	}
	baseTest.setupDispatchersFromKoin()
}
