package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Project
import kotlin.time.Instant

fun Project.parseLastSync(): Instant = last_sync
