package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.UpdateSource

/**
 * Signal emitted when a scene buffer is persisted to its temp (autosave) file, so callers can
 * hang side-effects (writing-activity timestamps, stats) off saves without this repository
 * reaching up into those collaborators. Full-save side-effects are handled inline by the
 * caller of [persistBuffer]. [content] is what was persisted, at buffer [revision]; the buffer may
 * have moved on.
 */
data class BufferPersistedEvent(
	val sceneId: Int,
	val source: UpdateSource,
	val content: SceneContent,
	val revision: Long,
)