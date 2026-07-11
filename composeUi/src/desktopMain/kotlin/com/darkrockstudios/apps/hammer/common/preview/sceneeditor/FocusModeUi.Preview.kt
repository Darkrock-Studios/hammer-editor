package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusMode
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.fakeSceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.focusmode.FocusModeUi

@Preview
@Composable
fun ScreenFocusModeUiPreview() {
	FocusModeUi(focusMode)
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenFocusModeUiTabletPreview() {
	TabletPreviewSurface {
		FocusModeUi(focusMode)
	}
}

private val focusMode = object : FocusMode {
	override val state = MutableValue(
		FocusMode.State(
			projectDef = fakeProjectDef(),
			sceneItem = fakeSceneItem(),
			sceneBuffer = SceneBuffer(
				content = SceneContent(
					scene = fakeSceneItem(),
					markdown = "This is some demo text for the focus mode preview."
				),
				source = UpdateSource.Editor
			),
			isLoading = false,
			textSize = 24f,
			spellChecker = null,
			spellCheckingEnabled = false
		)
	)
	override var lastForceUpdate = MutableValue(1L)

	override fun dismiss() {}
	override fun onContentChanged(content: PlatformRichText) {}
	override fun decreaseTextSize() {}
	override fun increaseTextSize() {}
	override fun resetTextSize() {}
}