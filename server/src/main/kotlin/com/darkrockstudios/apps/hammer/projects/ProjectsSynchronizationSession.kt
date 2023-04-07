package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.syncsessionmanager.SynchronizationSession
import kotlinx.datetime.Instant

data class ProjectsSynchronizationSession(
	override val userId: Long,
	override val started: Instant,
	override val syncId: String
) : SynchronizationSession(userId, started, syncId)