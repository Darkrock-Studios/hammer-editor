package com.darkrockstudios.apps.hammer.common.data.nux

import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

/**
 * Whether to show the first-run intro: only to a new user, one who has not finished or skipped it,
 * still has the example project, and has no other. Anyone else is marked as having seen it, so it
 * never turns up later. Reads files, so call it off the main thread.
 */
class ResolveIntroUseCase(
	private val nuxRepository: NuxRepository,
	private val projectsRepository: ProjectsRepository,
) {
	suspend operator fun invoke(): Boolean {
		// By name only: isExampleProject needs a flag that is set asynchronously during the first launch.
		val newUser = !nuxRepository.isIntroShown() && projectsRepository.isOnlyProject(ExampleProjectRepository.PROJECT_NAME)
		if (!newUser) nuxRepository.markIntroShown()
		return newUser
	}
}
