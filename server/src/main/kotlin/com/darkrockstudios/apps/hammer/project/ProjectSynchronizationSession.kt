package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.syncsessionmanager.SynchronizationSession
import kotlinx.datetime.Instant

data class ProjectSynchronizationSession(
	override val userId: Long,
	val projectDef: ProjectDefinition,
	override val started: Instant,
	override val syncId: String
) : SynchronizationSession(userId, started, syncId)