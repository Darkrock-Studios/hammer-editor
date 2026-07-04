package com.darkrockstudios.apps.hammer.common.data.ideasrepository.idea

import com.darkrockstudios.apps.hammer.base.IdeaId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class StoryIdea(
	val id: IdeaId,
	val created: Instant,
	val updated: Instant,
	val title: String? = null,
	val content: String,
	val tags: Set<String> = emptySet(),
	val promoted: Instant? = null,
	val archived: Instant? = null,
) {
	companion object {
		const val MAX_CONTENT_LENGTH = 10_000
	}
}
