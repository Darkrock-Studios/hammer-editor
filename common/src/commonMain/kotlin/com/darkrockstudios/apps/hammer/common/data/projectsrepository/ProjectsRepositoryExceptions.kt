package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import dev.icerock.moko.resources.StringResource

class ValidationFailedException(val errorMessage: StringResource) : IllegalArgumentException()
class ProjectCreationFailedException(val errorMessage: StringResource?) : IllegalArgumentException()
class ProjectRenameFailed(val reason: Reason) : Exception() {
	enum class Reason {
		InvalidName, AlreadyExists, SourceDoesNotExist, MoveFailed
	}
}