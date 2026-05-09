package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
	val currentDraftName = metadataState.metadata.currentDraftName
	val isValidDraftName = remember(draftName) {
		component.sceneMetadataComponent.validateDraftName(draftName)
	}
	val currentDraftIsValid = remember(currentDraftName) {
		component.sceneMetadataComponent.validateDraftName(currentDraftName)
	}

	if (!state.isSavingDraft) return

	fun close() {
		component.endSaveDraft()
		draftName = ""
	}

	fun submit() {
		if (!isValidDraftName || !currentDraftIsValid) return
		scope.launch {
			if (component.saveDraft(currentDraftName, draftName)) {
				showSnackbar(strRes.get(Res.string.save_draft_dialog_toast_success))
				component.endSaveDraft()
				withContext(mainDispatcher) { draftName = "" }
			}
		}
	}

	FormDialog(
		visible = true,
		marker = "§ SAVE",
		meta = "DRAFT",
		title = Res.string.save_draft_dialog_title.get(),
		confirmLabel = Res.string.save_draft_dialog_save_button.get(),
		cancelLabel = Res.string.save_draft_dialog_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = ::close,
		onDismiss = ::close,
		confirmEnabled = isValidDraftName && currentDraftIsValid,
	) {
		// Read-only "current draft" status block — uses the same mono-caps-label vocabulary.
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			Text(
				text = Res.string.save_draft_dialog_current_draft.get().uppercase(),
				fontFamily = FontFamily.Monospace,
				fontSize = 10.sp,
				letterSpacing = 1.8.sp,
				color = if (currentDraftIsValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
			)
			Text(
				text = "\"$currentDraftName\"",
				style = MaterialTheme.typography.titleMedium.copy(
					fontWeight = FontWeight.Normal,
					letterSpacing = (-0.16).sp,
				),
				color = MaterialTheme.colorScheme.onSurface,
			)
			if (!currentDraftIsValid) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier.padding(top = 2.dp),
				) {
					Icon(
						Icons.Filled.Error,
						tint = MaterialTheme.colorScheme.error,
						contentDescription = null,
						modifier = Modifier.padding(end = 2.dp),
					)
					Text(
						text = Res.string.save_draft_dialog_error.get(),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.error,
					)
				}
			}
		}

		FormField(
			value = draftName,
			onValueChange = { draftName = it },
			label = "NEW DRAFT NAME",
			placeholder = Res.string.save_draft_dialog_name_hint.get(),
			autoFocus = true,
			error = if (draftName.isNotEmpty() && !isValidDraftName) {
				Res.string.save_draft_dialog_error.get()
			} else null,
			onImeAction = ::submit,
		)
	}
}
