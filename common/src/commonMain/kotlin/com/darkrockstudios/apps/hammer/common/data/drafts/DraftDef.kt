package com.darkrockstudios.apps.hammer.common.data.drafts

import kotlinx.datetime.Instant

data class DraftDef(
    val sceneId: Int,
    val draftTimestamp: Instant,
    val draftName: String
)