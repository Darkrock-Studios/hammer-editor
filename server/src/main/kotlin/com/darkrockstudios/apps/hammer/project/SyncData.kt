package com.darkrockstudios.apps.hammer.project

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SyncData(
    val lastSync: Instant = Instant.DISTANT_PAST,
)
