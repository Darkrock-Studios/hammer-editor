package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.syncsessionmanager.SynchronizationSession
import kotlin.time.Instant

data class ProjectsSynchronizationSession(
	override val userId: Long,
	override val started: Instant,
	override val syncId: String,
	val installId: String? = null,
) : SynchronizationSession(userId, started, syncId)