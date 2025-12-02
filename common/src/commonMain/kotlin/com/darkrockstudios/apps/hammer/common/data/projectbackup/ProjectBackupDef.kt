package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlin.time.Instant

data class ProjectBackupDef(
	val path: HPath,
	val projectDef: ProjectDef,
	val date: Instant,
)