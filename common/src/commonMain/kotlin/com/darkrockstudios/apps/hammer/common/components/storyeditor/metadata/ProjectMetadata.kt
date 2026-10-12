package com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata

import com.darkrockstudios.apps.hammer.base.ProjectId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ProjectMetadata(
	val info: Info
) {
	companion object {
		const val FILENAME = "project.toml"
	}
}

@Serializable
data class Info(
	val created: Instant,
	val lastAccessed: Instant? = null,
	val dataVersion: Int = 0, // Default to 0, if we don't know the version, assume its the oldest
	/**
	 * Null only in a file that predates project ids or lost the field to a hand edit. Read it through
	 * `ProjectMetadataDatasource.loadProjectId`, which mints and saves one when it is missing.
	 */
	val projectId: ProjectId? = null,
)
