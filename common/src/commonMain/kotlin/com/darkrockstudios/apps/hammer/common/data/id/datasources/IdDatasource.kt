package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef

interface IdDatasource {
	fun findHighestId(projectDef: ProjectDef): Int
}