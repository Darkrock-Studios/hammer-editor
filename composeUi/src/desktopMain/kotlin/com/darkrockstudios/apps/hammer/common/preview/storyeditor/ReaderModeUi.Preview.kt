package com.darkrockstudios.apps.hammer.common.preview.storyeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.storyeditor.readermode.ReaderMode
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.ReaderModeUi

@Preview
@Composable
fun ScreenReaderModeUiPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
				ReaderModeUi(fakeReader(readerState()))
			}
		}
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenReaderModeUiTabletPreview() {
	KoinApplicationPreview {
		TabletPreviewSurface {
			ReaderModeUi(fakeReader(readerState().copy(showSceneHeadings = true, fontSize = 20f)))
		}
	}
}

@Preview
@Composable
fun ScreenReaderModeUiEmptyChapterPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
				ReaderModeUi(
					fakeReader(readerState().copy(activeChapterId = darkNight.id, scenes = emptyList()))
				)
			}
		}
	}
}

private fun item(id: Int, name: String, type: SceneItem.Type) = SceneItem(
	projectDef = fakeProjectDef(),
	type = type,
	id = id,
	name = name,
	order = id,
)

private val arrival = item(1, "Arrival", SceneItem.Type.Group)
private val harbour = item(2, "The Harbour", SceneItem.Type.Scene)
private val lampRoom = item(3, "The Lamp Room", SceneItem.Type.Scene)
private val darkNight = item(4, "The Dark Night", SceneItem.Type.Group)

private val harbourText = """
The keeper stepped off the ferry into a town that already knew his name. The gulls knew it too, or seemed to, wheeling over the quay and crying it back at him in pieces.

"You'll be wanting the light," said the woman at the chandlery, not looking up. She had the **key** on the counter before he had finished his breath.

He took it. It was heavier than a key had any right to be, and *cold*, as if it had been kept somewhere out of the sun for a long time.
""".trimIndent()

private val lampRoomText = """
One hundred and nine steps. He counted them the first night and never again, because the number did not change and the counting made the stair feel longer.

The lamp room smelled of oil and old brass. Somebody had left a chair facing the sea, and a cup beside it, and in the cup a dry brown ring where the tea had been.
""".trimIndent()

private fun readerState() = ReaderMode.State(
	chapters = listOf(
		ReaderMode.ReaderChapter(arrival, sceneCount = 2),
		ReaderMode.ReaderChapter(darkNight, sceneCount = 0),
	),
	activeChapterId = arrival.id,
	scenes = listOf(
		ReaderMode.ReaderScene(harbour, harbourText),
		ReaderMode.ReaderScene(lampRoom, lampRoomText),
	),
	isLoading = false,
)

private fun fakeReader(state: ReaderMode.State) = object : ReaderMode {
	override val state: Value<ReaderMode.State> = MutableValue(state)
	override fun showChapter(chapterId: Int) {}
	override fun nextChapter() {}
	override fun previousChapter() {}
	override fun showScene(sceneItem: SceneItem) {}
	override fun scrollHandled() {}
	override fun sceneInView(sceneItem: SceneItem?) {}
	override fun editScene(sceneItem: SceneItem?) {}
	override fun toggleSceneHeadings() {}
	override fun increaseFontSize() {}
	override fun decreaseFontSize() {}
	override fun resetFontSize() {}
	override fun setEditorMaxWidth(width: Float) {}
	override fun resetEditorMaxWidth() {}
	override fun close() {}
}
