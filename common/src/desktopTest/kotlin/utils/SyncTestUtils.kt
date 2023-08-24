package utils

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectsync.toSceneType

fun SceneItem.Companion.fromApiEntity(
	entity: ApiProjectEntity.SceneEntity,
	projectDef: ProjectDef
) = SceneItem(
	id = entity.id,
	name = entity.name,
	order = entity.order,
	projectDef = projectDef,
	type = entity.sceneType.toSceneType()
)