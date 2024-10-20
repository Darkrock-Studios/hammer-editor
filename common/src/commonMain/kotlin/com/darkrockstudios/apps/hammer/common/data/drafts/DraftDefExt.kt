package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

fun ApiProjectEntity.SceneDraftEntity.toDraftDef(): DraftDef {
	return DraftDef(
		id = id,
		sceneId = sceneId,
		draftTimestamp = created,
		draftName = name
	)
}