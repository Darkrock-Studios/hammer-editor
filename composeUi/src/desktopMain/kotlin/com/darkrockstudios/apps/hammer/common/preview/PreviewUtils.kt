package com.darkrockstudios.apps.hammer.common.preview

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.projecteditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.projecteditor.metadata.ProjectMetadata
import kotlinx.datetime.Instant

fun fakeProjectDef(): ProjectDef = ProjectDef(
	name = "Test",
	path = HPath(
		name = "Test",
		path = "/",
		isAbsolute = true
	)
)

fun fakeProjectMetadata(): ProjectMetadata = ProjectMetadata(
	info = Info(
		created = Instant.DISTANT_FUTURE
	)
)


fun fakeSceneItem() = SceneItem(
	projectDef = fakeProjectDef(),
	type = SceneItem.Type.Scene,
	id = 0,
	name = "Test Scene",
	order = 0
)
