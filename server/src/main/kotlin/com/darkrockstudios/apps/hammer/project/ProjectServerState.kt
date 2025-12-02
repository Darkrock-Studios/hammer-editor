package com.darkrockstudios.apps.hammer.project

import kotlin.time.Instant

data class ProjectServerState(val lastSync: Instant, val lastId: Int)