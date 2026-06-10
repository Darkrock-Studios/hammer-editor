package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.syncsessionmanager.SynchronizationSession
import kotlin.time.Instant

data class ProjectSynchronizationSession(
	override val userId: Long,
	val projectDef: ProjectDefinition,
	override val started: Instant,
	override val syncId: String,
	// The install that began the session. Only this install may reclaim it before it expires.
	val installId: String? = null,
) : SynchronizationSession(userId, started, syncId)