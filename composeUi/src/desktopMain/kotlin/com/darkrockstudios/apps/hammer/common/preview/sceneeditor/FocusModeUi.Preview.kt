package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusMode
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.fakeSceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.focusmode.FocusModeUi

@Preview
@Composable
fun FocusModeUiPreview() {
	FocusModeUi(focusMode)
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