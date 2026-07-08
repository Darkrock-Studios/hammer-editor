package com.darkrockstudios.apps.hammer.base.http.storyideas

import com.darkrockstudios.apps.hammer.base.IdeaId
import kotlinx.serialization.Serializable

/** One idea's identity and content hash, as known to the server. */
@Serializable
data class IdeaHashItem(
	val id: IdeaId,
	val hash: String,
)

/** The server's complete idea state for the account, fetched at the start of the ideas sync phase. */
@Serializable
data class IdeasSyncStateResponse(
	val ideas: List<IdeaHashItem>,
	val deletedIdeas: Set<IdeaId>,
)

@Serializable
data class SavedIdeaDto(
	val idea: StoryIdea,
	val hash: String,
)

/**
 * [originalHash] is the client's conflict baseline — the hash last agreed with the server. Null
 * when this client has never synced this idea before (baseline-less upload). [hash] is the
 * client-computed [com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeaHasher] hash of
 * [idea]; the server stores it verbatim alongside the payload.
 */
@Serializable
data class IdeaUploadRequest(
	val idea: StoryIdea,
	val hash: String,
	val originalHash: String? = null,
)

/** 409 payload: the server's copy, for the user to resolve against. */
@Serializable
data class IdeaConflictDto(
	val server: StoryIdea,
	val serverHash: String,
)
