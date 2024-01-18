package com.darkrockstudios.apps.hammer.common.preview

import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.StrRes
import dev.icerock.moko.resources.StringResource
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
		created = Instant.DISTANT_FUTURE,
		lastAccessed = Instant.DISTANT_FUTURE,
	)
)

fun fakeSceneItem() = SceneItem(
	projectDef = fakeProjectDef(),
	type = SceneItem.Type.Scene,
	id = 0,
	name = "Test Scene",
	order = 0
)

class PreviewStrRes : StrRes {
	override fun get(str: StringResource): String = ""
	override fun get(str: StringResource, vararg args: Any): String = ""
}