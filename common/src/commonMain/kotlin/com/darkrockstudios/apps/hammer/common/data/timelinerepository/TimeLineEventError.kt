package com.darkrockstudios.apps.hammer.common.data.timelinerepository

enum class TimeLineEventError {
	NONE,
	TAG_TOO_LONG;

	val isSuccess: Boolean
		get() = this == NONE
}
