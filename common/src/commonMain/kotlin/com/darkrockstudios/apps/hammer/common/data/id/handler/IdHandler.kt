package com.darkrockstudios.apps.hammer.common.data.id.handler

import com.darkrockstudios.apps.hammer.common.data.ProjectDef

interface IdHandler {
	fun findHighestId(projectDef: ProjectDef): Int
}