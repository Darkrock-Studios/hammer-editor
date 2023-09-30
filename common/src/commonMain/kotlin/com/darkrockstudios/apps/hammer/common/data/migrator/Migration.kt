package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.common.data.ProjectDef

interface Migration {
	val toVersion: Int
	fun migrate(projectDef: ProjectDef)
}