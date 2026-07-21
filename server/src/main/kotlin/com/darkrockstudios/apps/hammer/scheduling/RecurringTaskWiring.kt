package com.darkrockstudios.apps.hammer.scheduling

import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject

/**
 * Starts [task] on a supervised scope parented to the application's job,
 * registers it with the [RecurringTaskRegistry] so the admin dashboard can
 * report its status, and wires graceful shutdown: when the application stops,
 * the in-flight tick is allowed to finish before the scope is cancelled, so a
 * tick can't outlive the application and mutate state afterwards.
 *
 * The [ApplicationStopping] hook is the graceful path. Parenting the scope to
 * the application job is the backstop: a caller that tears the application down
 * without the lifecycle event still cannot leave the loop running.
 */
fun Application.launchRecurringTask(task: RecurringTask) {
	val registry: RecurringTaskRegistry by inject()
	registry.register(task)

	val scope = CoroutineScope(SupervisorJob(coroutineContext[Job]) + Dispatchers.Default)
	task.start(scope)

	monitor.subscribe(ApplicationStopping) {
		runBlocking { task.stop() }
		scope.cancel()
	}
}
