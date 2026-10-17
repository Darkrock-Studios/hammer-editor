package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Names its project rather than defining it: the project may no longer exist, so may have no id. */
@Serializable
data class ProjectBackupDef(
	val path: HPath,
	val projectName: String,
	val date: Instant,
)
