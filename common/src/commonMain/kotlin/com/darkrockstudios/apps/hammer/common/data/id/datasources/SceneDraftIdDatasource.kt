package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import kotlinx.coroutines.runBlocking

class SceneDraftIdDatasource(
	private val sceneDraftsDatasource: SceneDraftsDatasource,
) : IdDatasource {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val sceneDraftDir = runBlocking { sceneDraftsDatasource.getAllDrafts() }
		val maxId: Int = sceneDraftDir.maxOfOrNull { it.id } ?: -1
		return maxId
	}
}