package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

data class EntityDefinition(
	val id: Int,
	val type: ApiProjectEntity.Type,
)