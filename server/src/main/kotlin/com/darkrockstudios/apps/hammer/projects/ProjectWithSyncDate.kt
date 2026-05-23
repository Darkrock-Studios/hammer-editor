package com.darkrockstudios.apps.hammer.projects

import kotlin.time.Instant

data class ProjectWithSyncDate(
	val name: String,
	val uuid: String,
	val lastSync: Instant,
)
