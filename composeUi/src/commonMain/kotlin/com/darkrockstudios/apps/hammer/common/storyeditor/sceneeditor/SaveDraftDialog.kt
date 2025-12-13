package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
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

	val metadataState by component.sceneMetadataComponent.state.subscribeAsState()
	val isValidDraftName = remember(draftName) {
		component.sceneMetadataComponent.validateDraftName(draftName)
	}

	if (state.isSavingDraft) {
		AlertDialog(
			onDismissRequest = {
				component.endSaveDraft()
				draftName = ""
			},
			icon = {
				Icon(Icons.Filled.Save, contentDescription = null)
			},
			title = {
				Text(text = Res.string.save_draft_dialog_title.get())
			},
			text = {
				Column(
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)
				) {
					val currentDraftIsValid = remember(metadataState.metadata.currentDraftName) {
						component.sceneMetadataComponent.validateDraftName(metadataState.metadata.currentDraftName)
					}

					Surface(
						shape = MaterialTheme.shapes.medium,
						color = if (currentDraftIsValid) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
						modifier = Modifier.fillMaxWidth()
					) {
						Column(
							modifier = Modifier.padding(Ui.Padding.M)
						) {
							Text(
								text = Res.string.save_draft_dialog_current_draft.get(),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
							Text(
								text = "\"${metadataState.metadata.currentDraftName}\"",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.fillMaxWidth(),
								textAlign = TextAlign.Center
							)
							if (!currentDraftIsValid) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									verticalAlignment = Alignment.CenterVertically
								) {
									Icon(
										Icons.Filled.Error,
										tint = MaterialTheme.colorScheme.error,
										contentDescription = null
									)
									Text(
										modifier = Modifier.padding(start = Ui.Padding.M),
										text = Res.string.save_draft_dialog_error.get(),
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.error
									)
								}
							}
						}
					}

					OutlinedTextField(
						value = draftName,
						onValueChange = { draftName = it },
						singleLine = true,
						label = { Text("New Draft Name") },
						placeholder = { Text(Res.string.save_draft_dialog_name_hint.get()) },
						modifier = Modifier.fillMaxWidth(),
						shape = MaterialTheme.shapes.medium
					)
				}
			},
			confirmButton = {
				Button(
					onClick = {
						scope.launch {
							if (component.saveDraft(metadataState.metadata.currentDraftName, draftName)) {
								showSnackbar(strRes.get(Res.string.save_draft_dialog_toast_success))
								component.endSaveDraft()
								withContext(mainDispatcher) {
									draftName = ""
								}
							}
						}
					},
					enabled = isValidDraftName && component.sceneMetadataComponent.validateDraftName(metadataState.metadata.currentDraftName)
				) {
					Text(Res.string.save_draft_dialog_save_button.get())
				}
			},
			dismissButton = {
				TextButton(onClick = {
					component.endSaveDraft()
					draftName = ""
				}) {
					Text(Res.string.save_draft_dialog_cancel_button.get())
				}
			}
		)
	}
}