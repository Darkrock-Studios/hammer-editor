package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveDraftDialog(
	state: SceneEditor.State,
	component: SceneEditor,
	showSnackbar: (message: String) -> Unit
) {
	val strRes = rememberStrRes()
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	var draftName by remember { mutableStateOf("") }

	SimpleDialog(
		visible = state.isSavingDraft,
		title = MR.strings.save_draft_dialog_title.get(),
		onCloseRequest = {
			component.endSaveDraft()
			draftName = ""
		}
	) {
		Box(modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL)) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
			) {
				TextField(
					value = draftName,
					onValueChange = { draftName = it },
					singleLine = true,
					placeholder = { Text(MR.strings.save_draft_dialog_name_hint.get()) }
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(onClick = {
						scope.launch {
							if (component.saveDraft(draftName)) {
								component.endSaveDraft()
								withContext(mainDispatcher) {
									draftName = ""
								}
								showSnackbar(strRes.get(MR.strings.save_draft_dialog_toast_success))
							}
						}
					}) {
						Text(MR.strings.save_draft_dialog_save_button.get())
					}
					Button(onClick = {
						component.endSaveDraft()
						draftName = ""
					}) {
						Text(MR.strings.save_draft_dialog_cancel_button.get())
					}
				}
			}
		}
	}
}