package com.darkrockstudios.apps.hammer.common.preview.storyeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview.OutlineOverview
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.storyeditor.OutlineOverviewContent

@Preview
@Composable
fun ScreenOutlineOverviewUiPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			OutlineOverviewContent(component, onDismiss = {})
		}
	}
}

private fun scene(id: Int, order: Int, name: String, type: SceneItem.Type) = SceneItem(
	projectDef = fakeProjectDef(),
	type = type,
	id = id,
	name = name,
	order = order,
)

private val overview = listOf(
	OutlineOverview.OutlineItem.ChapterOutline(
		scene(1, 0, "Arrival", SceneItem.Type.Group),
	),
	OutlineOverview.OutlineItem.SceneOutline(
		scene(2, 0, "The Harbour", SceneItem.Type.Scene),
		outline = "The keeper steps off the ferry into a town that already knows his name.",
	),
	OutlineOverview.OutlineItem.SceneOutline(
		scene(3, 1, "The Lamp Room", SceneItem.Type.Scene),
		outline = null,
	),
	OutlineOverview.OutlineItem.ChapterOutline(
		scene(4, 1, "The Dark Night", SceneItem.Type.Group),
	),
	OutlineOverview.OutlineItem.SceneOutline(
		scene(5, 0, "Wreck", SceneItem.Type.Scene),
		outline = "The lamp fails and the village wakes to the sound of splintering hull.",
	),
)

private val component = object : OutlineOverview {
	override val state: Value<OutlineOverview.State> = MutableValue(
		OutlineOverview.State(overview = overview)
	)

	override fun dismiss() {}
	override fun selectScene(sceneItem: SceneItem) {}
}
