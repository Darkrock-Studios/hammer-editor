package com.darkrockstudios.apps.hammer.common.data.drafts

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class DraftDef(
	val id: Int,
	val sceneId: Int,
	val draftTimestamp: Instant,
	val draftName: String
)