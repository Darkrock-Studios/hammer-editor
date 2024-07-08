package com.darkrockstudios.apps.hammer.project

import kotlinx.datetime.Instant

data class ProjectServerState(val lastSync: Instant, val lastId: Int)