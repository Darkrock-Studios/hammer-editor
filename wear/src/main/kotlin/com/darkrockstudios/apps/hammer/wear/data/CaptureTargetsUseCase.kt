package com.darkrockstudios.apps.hammer.wear.data

/**
 * Where a note could go right now. [default] is the project last captured to, falling back to the
 * first one on the watch, so dictating is one tap and choosing is optional.
 */
data class CaptureTargets(
	val projects: List<WatchProject> = emptyList(),
	val default: WatchProject? = null,
)

class CaptureTargetsUseCase(
	private val listProjects: ListWatchProjectsUseCase,
	private val subscriptions: SubscribedProjectsRepository,
) {
	suspend fun load(): CaptureTargets {
		// Only subscribed projects: an unsynced one has an empty IdAllocator, so a note there
		// would take id 1 and collide on every later sync.
		val projects = listProjects.list().filter { it.subscribed }
		val lastUsed = subscriptions.lastCaptureProjectId()
		return CaptureTargets(
			projects = projects,
			default = projects.find { it.projectId == lastUsed } ?: projects.firstOrNull(),
		)
	}
}
