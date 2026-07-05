package com.darkrockstudios.apps.hammer.common.data.ideasrepository

enum class IdeaError {
	NONE,
	EMPTY,
	TOO_LONG,
	TAG_TOO_LONG;

	val isSuccess: Boolean
		get() = this == NONE
}

class InvalidIdea(val error: IdeaError) : Exception("Idea failed validation: $error")
