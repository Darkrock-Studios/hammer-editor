package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import kotlinx.datetime.Instant

fun Project.parseLastSync(): Instant {
	return sqliteDateTimeStringToInstant(last_sync)
}
