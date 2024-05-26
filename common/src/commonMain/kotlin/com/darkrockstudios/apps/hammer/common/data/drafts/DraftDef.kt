package com.darkrockstudios.apps.hammer.common.data.drafts

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class DraftDef(
	val id: Int,
	val sceneId: Int,
	val draftTimestamp: Instant,
	val draftName: String
)