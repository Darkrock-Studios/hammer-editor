package com.darkrockstudios.apps.hammer.scheduling

import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

/**
 * Starts [task] on its own supervised scope and wires graceful shutdown: when the
 * application stops, the in-flight tick is allowed to finish before the scope is
 * cancelled, so a tick can't outlive the application and mutate state afterwards.
 */
fun Application.launchRecurringTask(task: RecurringTask) {
	val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	task.start(scope)

	environment.monitor.subscribe(ApplicationStopped) {
		runBlocking { task.stop() }
		scope.cancel()
	}
}
