package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import org.jetbrains.compose.resources.StringResource

class ValidationFailedException(val errorMessage: StringResource) : IllegalArgumentException()
class ProjectCreationFailedException(val errorMessage: StringResource?) : IllegalArgumentException()
class ProjectRenameFailed(val reason: Reason) : Exception() {
	enum class Reason {
		InvalidName, AlreadyExists, SourceDoesNotExist, MoveFailed, ProjectInUse
	}
}

/** A project's identity may not change while it has a project scope open. */
class ProjectInUseException(projectName: String) :
	IllegalStateException("Project is in use, its id cannot change: $projectName")
