package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_DESCRIPTION_LENGTH
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.create_group_button
import com.darkrockstudios.apps.hammer.create_group_cancel_button
import com.darkrockstudios.apps.hammer.create_group_description_heading
import com.darkrockstudios.apps.hammer.create_group_name_heading
import com.darkrockstudios.apps.hammer.create_group_title
import com.darkrockstudios.apps.hammer.create_project_meta_in_group
import com.darkrockstudios.apps.hammer.delete_group_body
import com.darkrockstudios.apps.hammer.delete_group_cancel
import com.darkrockstudios.apps.hammer.delete_group_confirm
import com.darkrockstudios.apps.hammer.delete_group_title
import com.darkrockstudios.apps.hammer.group_description_cancel_button
import com.darkrockstudios.apps.hammer.group_description_heading
import com.darkrockstudios.apps.hammer.group_description_helper
import com.darkrockstudios.apps.hammer.group_description_save_button
import com.darkrockstudios.apps.hammer.group_description_title
import com.darkrockstudios.apps.hammer.group_description_too_long
import com.darkrockstudios.apps.hammer.rename_group_button
import com.darkrockstudios.apps.hammer.rename_group_cancel_button
import com.darkrockstudios.apps.hammer.rename_group_heading
import com.darkrockstudios.apps.hammer.rename_group_title
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import androidx.compose.ui.text.input.KeyboardCapitalization

const val CreateGroupNameFieldTestTag = "create-group-name"

/** The line under a dialog title when it acts inside [parent]: "In Trilogy"; nothing at the top level. */
@Composable
internal fun groupMeta(parent: GroupDef?): String? =
	parent?.let { Res.string.create_project_meta_in_group.get(it.name) }

/** These dialogs leave composition on dismiss, so they always show and have no exit animation. */
@Composable
private fun GroupDialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
	AnimatedDialog(
		visible = true,
		onCloseRequest = onDismiss,
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		content()
	}
}

@Composable
fun GroupCreateDialog(
	parent: GroupDef?,
	onCreate: (name: String, description: String?) -> Unit,
	onDismiss: () -> Unit,
) {
	var name by rememberSaveable { mutableStateOf("") }
	var description by rememberSaveable { mutableStateOf("") }
	val validation = rememberNameValidation(name, NameKind.Project)

	GroupDialogFrame(onDismiss) {
		GroupCreateContent(
			parentName = parent?.name,
			name = name,
			onNameChange = { name = it },
			nameError = validation.fieldError(name),
			nameValid = validation.isValid,
			description = description,
			onDescriptionChange = { description = it },
			onCreate = onCreate,
			onCancel = onDismiss,
		)
	}
}

@Composable
fun GroupCreateContent(
	parentName: String?,
	name: String,
	onNameChange: (String) -> Unit,
	nameError: String?,
	nameValid: Boolean,
	description: String,
	onDescriptionChange: (String) -> Unit,
	onCreate: (name: String, description: String?) -> Unit,
	onCancel: () -> Unit,
) {
	val descriptionError = descriptionError(description)
	val canCreate = nameValid && descriptionError == null

	fun submit() {
		if (canCreate) onCreate(name, description.ifBlank { null })
	}

	FormDialogScaffold(
		title = Res.string.create_group_title.get(),
		meta = parentName?.let { Res.string.create_project_meta_in_group.get(it) },
		confirmLabel = Res.string.create_group_button.get(),
		cancelLabel = Res.string.create_group_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = onCancel,
		confirmEnabled = canCreate,
	) {
		HdHairlineField(
			value = name,
			onValueChange = onNameChange,
			label = Res.string.create_group_name_heading.get(),
			autoFocus = true,
			error = nameError,
			onImeAction = ::submit,
			testTag = CreateGroupNameFieldTestTag,
			imeAction = ImeAction.Done,
			capitalization = KeyboardCapitalization.None,
		)
		HdHairlineField(
			value = description,
			onValueChange = onDescriptionChange,
			label = Res.string.create_group_description_heading.get(),
			singleLine = false,
			imeAction = ImeAction.Default,
			error = descriptionError,
			capitalization = KeyboardCapitalization.None,
		)
	}
}

@Composable
fun GroupRenameDialog(
	groupDef: GroupDef,
	onRename: (newName: String) -> Unit,
	onDismiss: () -> Unit,
) {
	var name by rememberSaveable { mutableStateOf(groupDef.name) }
	val validation = rememberNameValidation(name, NameKind.Project)

	GroupDialogFrame(onDismiss) {
		GroupRenameContent(
			currentName = groupDef.name,
			name = name,
			onNameChange = { name = it },
			nameError = validation.fieldError(name),
			nameValid = validation.isValid,
			onRename = onRename,
			onCancel = onDismiss,
		)
	}
}

@Composable
fun GroupRenameContent(
	currentName: String,
	name: String,
	onNameChange: (String) -> Unit,
	nameError: String?,
	nameValid: Boolean,
	onRename: (newName: String) -> Unit,
	onCancel: () -> Unit,
) {
	fun submit() {
		if (nameValid) onRename(name)
	}

	FormDialogScaffold(
		title = Res.string.rename_group_title.get(),
		subject = currentName,
		confirmLabel = Res.string.rename_group_button.get(),
		cancelLabel = Res.string.rename_group_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = onCancel,
		confirmEnabled = nameValid,
	) {
		HdHairlineField(
			value = name,
			onValueChange = onNameChange,
			label = Res.string.rename_group_heading.get(),
			autoFocus = true,
			error = nameError,
			onImeAction = ::submit,
			imeAction = ImeAction.Done,
			capitalization = KeyboardCapitalization.None,
		)
	}
}

@Composable
fun GroupDescriptionDialog(
	groupDef: GroupDef,
	onSave: (description: String?) -> Unit,
	onDismiss: () -> Unit,
) {
	var description by rememberSaveable { mutableStateOf(groupDef.description.orEmpty()) }

	GroupDialogFrame(onDismiss) {
		GroupDescriptionContent(
			groupName = groupDef.name,
			description = description,
			onDescriptionChange = { description = it },
			onSave = onSave,
			onCancel = onDismiss,
		)
	}
}

@Composable
fun GroupDescriptionContent(
	groupName: String,
	description: String,
	onDescriptionChange: (String) -> Unit,
	onSave: (description: String?) -> Unit,
	onCancel: () -> Unit,
) {
	val error = descriptionError(description)

	fun submit() {
		if (error == null) onSave(description.ifBlank { null })
	}

	FormDialogScaffold(
		title = Res.string.group_description_title.get(),
		subject = groupName,
		confirmLabel = Res.string.group_description_save_button.get(),
		cancelLabel = Res.string.group_description_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = onCancel,
		confirmEnabled = error == null,
	) {
		HdHairlineField(
			value = description,
			onValueChange = onDescriptionChange,
			label = Res.string.group_description_heading.get(),
			autoFocus = true,
			singleLine = false,
			imeAction = ImeAction.Default,
			hint = Res.string.group_description_helper.get(),
			error = error,
			capitalization = KeyboardCapitalization.None,
		)
	}
}

/** Deleting a group keeps everything in it, so this is a plain confirmation, not a destructive one. */
@Composable
fun GroupDeleteDialog(
	groupDef: GroupDef,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	GroupDialogFrame(onDismiss) {
		GroupDeleteContent(groupName = groupDef.name, onConfirm = onConfirm, onCancel = onDismiss)
	}
}

@Composable
fun GroupDeleteContent(
	groupName: String,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) {
	FormDialogScaffold(
		title = Res.string.delete_group_title.get(),
		subject = groupName,
		confirmLabel = Res.string.delete_group_confirm.get(),
		cancelLabel = Res.string.delete_group_cancel.get(),
		onConfirm = onConfirm,
		onCancel = onCancel,
	) {
		Text(
			text = Res.string.delete_group_body.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun descriptionError(description: String): String? =
	if (description.length > MAX_GROUP_DESCRIPTION_LENGTH) {
		Res.string.group_description_too_long.get(MAX_GROUP_DESCRIPTION_LENGTH)
	} else {
		null
	}
