package com.darkrockstudios.apps.hammer.common.components.projectroot

sealed interface ProjectDeepLink {
	data class Scene(val sceneId: Int) : ProjectDeepLink
	data class Note(val noteId: Int) : ProjectDeepLink
	data class EncyclopediaEntry(val entryId: Int) : ProjectDeepLink
	data class TimelineEvent(val eventId: Int) : ProjectDeepLink
}
