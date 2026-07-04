package com.darkrockstudios.apps.hammer.base.http.storyideas

import com.darkrockstudios.apps.hammer.base.IdeaId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * An account-level story idea. Lives in `base` because it is also the sync wire format: the
 * client uploads the JSON-serialized [StoryIdea] and the server stores it verbatim as an opaque
 * blob (decoding it only to validate the payload shape), same as
 * [com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData].
 */
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
