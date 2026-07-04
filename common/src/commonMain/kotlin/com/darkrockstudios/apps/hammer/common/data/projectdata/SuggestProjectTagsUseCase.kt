package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

/**
 * Aggregates the project-level tags used across the user's other projects, so the tag field can
 * suggest a consistent vocabulary. Cross-project by nature, so it lives at the Koin root rather
 * than in a `ProjectDefScope`.
 */
class SuggestProjectTagsUseCase(
	private val projectsRepository: ProjectsRepository,
	private val fileSystem: FileSystem,
	private val toml: Toml,
) {
	suspend fun tagsFromOtherProjects(exclude: ProjectDef): Set<String> =
		projectsRepository.getProjects()
			.filter { it != exclude }
			.flatMap { loadStoredProjectData(it, fileSystem, toml).data.tags }
			.toSet()
}
