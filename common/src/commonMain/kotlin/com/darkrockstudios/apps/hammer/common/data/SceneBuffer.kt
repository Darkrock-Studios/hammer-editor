package com.darkrockstudios.apps.hammer.common.data

data class SceneBuffer(
	val content: SceneContent,
	val dirty: Boolean = false,
	val source: UpdateSource
)

enum class UpdateSource {
	Editor,
	Repository,
	Drafts,
	Sync
}