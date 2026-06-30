package com.darkrockstudios.apps.hammer.scheduling

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds the set of [RecurringTask]s that have been started, so the admin
 * dashboard can report each one's status. Tasks register themselves when
 * launched via [launchRecurringTask]; a task that never starts (e.g. Patreon
 * polling when the integration is disabled at the server level) does not appear.
 */
class RecurringTaskRegistry {
	private val tasks = CopyOnWriteArrayList<RecurringTask>()

	fun register(task: RecurringTask) {
		if (tasks.none { it.name == task.name }) {
			tasks.add(task)
		}
	}

	/** Snapshot of every registered task's status, ordered by name for a stable UI. */
	fun statuses(): List<RecurringTaskStatus> = tasks
		.map { it.status() }
		.sortedBy { it.name }
}
