package com.darkrockstudios.apps.hammer.storyideas

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire-identical to the client's typed `IdeaUploadRequest`/`SavedIdeaDto`/`IdeaConflictDto`
 * (base module, `http.storyideas`), but with the idea slot held as raw [JsonElement] so the
 * server never depends on the client's `StoryIdea` shape. See "Shape-agnostic server" in
 * docs/STORY-IDEAS.md.
 */
@Serializable
data class RawIdeaUploadRequest(
	val idea: JsonElement,
	val hash: String,
	val originalHash: String? = null,
)

@Serializable
data class RawSavedIdeaDto(
	val idea: JsonElement,
	val hash: String,
)

@Serializable
data class RawIdeaConflictDto(
	val server: JsonElement,
	val serverHash: String,
)
