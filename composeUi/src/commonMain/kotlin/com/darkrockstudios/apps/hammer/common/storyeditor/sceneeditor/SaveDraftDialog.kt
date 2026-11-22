package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

@Composable
internal fun SaveDraftDialog(
	state: SceneEditor.State,
	component: SceneEditor,
	showSnackbar: (message: String) -> Unit
) {
	val strRes = rememberStrRes()
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	var draftName by rememberSaveable { mutableStateOf("") }

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

	AnimatedDialog(
		visible = true,
		onCloseRequest = ::close,
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		SaveDraftCard(
			currentDraftName = currentDraftName,
			currentDraftIsValid = currentDraftIsValid,
			draftName = draftName,
			onDraftNameChange = { draftName = it },
			isValidDraftName = isValidDraftName,
			onConfirm = ::submit,
			onCancel = ::close,
			autoFocus = true,
		)
	}
}

@Composable
internal fun SaveDraftCard(
	currentDraftName: String,
	currentDraftIsValid: Boolean,
	draftName: String,
	onDraftNameChange: (String) -> Unit,
	isValidDraftName: Boolean,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
	autoFocus: Boolean = false,
) {
	FormDialogScaffold(
		title = Res.string.save_draft_dialog_title.get(),
		confirmLabel = Res.string.save_draft_dialog_save_button.get(),
		cancelLabel = Res.string.save_draft_dialog_cancel_button.get(),
		onConfirm = onConfirm,
		onCancel = onCancel,
		confirmEnabled = isValidDraftName && currentDraftIsValid,
	) {
		Text(
			text = Res.string.save_draft_dialog_current_draft_named.get(currentDraftName),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		if (!currentDraftIsValid) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
			) {
				Icon(
					Icons.Filled.Error,
					tint = MaterialTheme.colorScheme.error,
					contentDescription = null,
				)
				Text(
					text = Res.string.save_draft_dialog_error.get(),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
				)
			}
		}

		HdHairlineField(
			value = draftName,
			onValueChange = onDraftNameChange,
			label = Res.string.save_draft_dialog_name_label.get(),
			autoFocus = autoFocus,
			error = if (draftName.isNotEmpty() && !isValidDraftName) {
				Res.string.save_draft_dialog_name_error.get()
			} else null,
			onImeAction = onConfirm,
			imeAction = ImeAction.Done,
			capitalization = KeyboardCapitalization.None,
		)
	}
}
