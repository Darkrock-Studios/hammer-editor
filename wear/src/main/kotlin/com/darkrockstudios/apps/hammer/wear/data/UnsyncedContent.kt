package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.common.data.ProjectDef

/**
 * Counts writing the watch holds that the server has not accepted. Behind an interface because
 * reading a project's sync journal needs a full project scope, which a JVM test cannot open.
 */
interface UnsyncedContentSource {
	suspend fun pendingIn(projectDef: ProjectDef): Int
	suspend fun pendingIdeas(): Int
}

/** [projects] is keyed by project name and holds only projects with something outstanding. */
data class UnsyncedContent(
	val projects: Map<String, Int> = emptyMap(),
	val ideas: Int = 0,
) {
	val total: Int get() = projects.values.sum() + ideas
	val isEmpty: Boolean get() = total == 0
}

class UnsyncedContentUseCase(
	private val listProjects: ListWatchProjectsUseCase,
	private val source: UnsyncedContentSource,
) {
	suspend fun pendingIn(projectDef: ProjectDef): Int = source.pendingIn(projectDef)

	/** Only subscribed projects are counted; the rest hold no content on the watch. */
	suspend fun pending(): UnsyncedContent {
		val projects = listProjects.list()
			.filter { it.subscribed }
			.associate { it.projectDef.name to source.pendingIn(it.projectDef) }
			.filterValues { it > 0 }
		return UnsyncedContent(projects = projects, ideas = source.pendingIdeas())
	}
}
