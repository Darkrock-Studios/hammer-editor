package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeSceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SceneEditorUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.compose.resources.StringResource

@Preview
@Composable
fun SceneEditorUiPreview() {
	KoinApplicationPreview {
		val component = fakeComponent()
		val rootSnackbar = rememberRootSnackbarHostState()
		SceneEditorUi(component, rootSnackbar)
	}
}

private fun fakeProjectDef(): ProjectDef = ProjectDef(
	name = "Test",
	path = HPath(
		name = "Test",
		path = "/",
		isAbsolute = true
	)
)

private fun fakeComponent() = object : SceneEditor {
	override val state: Value<SceneEditor.State>
		get() = MutableValue(
			SceneEditor.State(
				sceneItem = fakeSceneItem(),
				isSavingDraft = false,
				isEditingName = false
			)
		)
	override var lastForceUpdate = MutableValue(0L)
	override val sceneMetadataComponent = object : SceneMetadataPanel {
		override val state = MutableValue(SceneMetadataPanel.State(fakeSceneItem()))
		override fun updateOutline(text: String) {}
		override fun updateNotes(text: String) {}
		override fun updateDraftName(text: String) {}
		override fun validateDraftName(text: String) = true
		override fun confirmReference(entryId: Int) {}
		override fun unconfirmReference(entryId: Int) {}
		override fun dismissReference(entryId: Int) {}
		override fun restoreDismissedReference(entryId: Int) {}
		override fun navigateToEntry(entryDef: EntryDef) {}
	}

	override fun addEditorMenu() {}
	override fun removeEditorMenu() {}
	override fun loadSceneContent() {}
	override suspend fun storeSceneContent() = true
	override fun onContentChanged(content: PlatformRichText) {}
	override fun beginSceneNameEdit() {}
	override fun endSceneNameEdit() {}
	override suspend fun changeSceneName(newName: String) {}
	override fun beginSaveDraft() {}
	override fun endSaveDraft() {}
	override suspend fun saveDraft(draftName: String, newDraftName: String) = true
	override val toast = MutableSharedFlow<ToastMessage>()
	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
	override fun showToast(scope: CoroutineScope, message: String) {}
	override fun showToast(scope: CoroutineScope, message: Msg) {}
	override suspend fun showToast(message: StringResource, vararg params: Any) {}
	override suspend fun showToast(message: String) {}
	override suspend fun showToast(message: Msg) {}
	override fun closeEditor() {}
	override fun beginDelete() {}
	override fun endDelete() {}
	override fun doDelete() {}
	override fun beginArchive() {}
	override fun endArchive() {}
	override fun doArchive() {}
	override fun beginDiscard() {}
	override fun endDiscard() {}
	override fun doDiscard() {}
	override fun toggleMetadataVisibility() {}
	override fun decreaseTextSize() {}
	override fun increaseTextSize() {}
	override fun resetTextSize() {}
	override fun enterFocusMode() {}
}