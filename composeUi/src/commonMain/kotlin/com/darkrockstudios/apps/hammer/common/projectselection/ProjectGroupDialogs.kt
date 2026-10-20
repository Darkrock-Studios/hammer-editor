package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_DESCRIPTION_LENGTH
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationDialog
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.create_group_button
import com.darkrockstudios.apps.hammer.create_group_cancel_button
import com.darkrockstudios.apps.hammer.create_group_description_heading
import com.darkrockstudios.apps.hammer.create_group_marker
import com.darkrockstudios.apps.hammer.create_group_meta
import com.darkrockstudios.apps.hammer.create_group_name_heading
import com.darkrockstudios.apps.hammer.create_group_title
import com.darkrockstudios.apps.hammer.create_project_meta_in_group
import com.darkrockstudios.apps.hammer.delete_group_cancel
import com.darkrockstudios.apps.hammer.delete_group_confirm
import com.darkrockstudios.apps.hammer.delete_group_message
import com.darkrockstudios.apps.hammer.delete_group_title
import com.darkrockstudios.apps.hammer.group_description_cancel_button
import com.darkrockstudios.apps.hammer.group_description_heading
import com.darkrockstudios.apps.hammer.group_description_helper
import com.darkrockstudios.apps.hammer.group_description_marker
import com.darkrockstudios.apps.hammer.group_description_save_button
import com.darkrockstudios.apps.hammer.group_description_title
import com.darkrockstudios.apps.hammer.group_description_too_long
import com.darkrockstudios.apps.hammer.rename_group_button
import com.darkrockstudios.apps.hammer.rename_group_cancel_button
import com.darkrockstudios.apps.hammer.rename_group_heading
import com.darkrockstudios.apps.hammer.rename_group_title

const val CreateGroupNameFieldTestTag = "create-group-name"

/** The masthead meta for a dialog acting inside [parent]: "IN TRILOGY", or [topLevel]. */
@Composable
internal fun groupMeta(parent: GroupDef?, topLevel: String): String =
	(parent?.let { Res.string.create_project_meta_in_group.get(it.name) } ?: topLevel).uppercase()

@Composable
fun GroupCreateDialog(
	parent: GroupDef?,
	onCreate: (name: String, description: String?) -> Unit,
	onDismiss: () -> Unit,
) {
	var name by rememberSaveable { mutableStateOf("") }
	var description by rememberSaveable { mutableStateOf("") }
	val validation = rememberNameValidation(name, NameKind.Project)
	val descriptionError = descriptionError(description)

	fun submit() {
		if (validation.isValid && descriptionError == null) onCreate(name, description.ifBlank { null })
	}

	FormDialog(
		visible = true,
		marker = "§ ${Res.string.create_group_marker.get().uppercase()}",
		meta = groupMeta(parent, Res.string.create_group_meta.get()),
		title = Res.string.create_group_title.get(),
		confirmLabel = Res.string.create_group_button.get(),
		cancelLabel = Res.string.create_group_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = onDismiss,
		onDismiss = onDismiss,
		confirmEnabled = validation.isValid && descriptionError == null,
	) {
		FormField(
			value = name,
			onValueChange = { name = it },
			label = Res.string.create_group_name_heading.get(),
			autoFocus = true,
			error = validation.fieldError(name),
			onImeAction = ::submit,
			testTag = CreateGroupNameFieldTestTag,
		)
		FormField(
			value = description,
			onValueChange = { description = it },
			label = Res.string.create_group_description_heading.get(),
			singleLine = false,
			imeAction = ImeAction.Default,
			error = descriptionError,
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

	fun submit() {
		if (validation.isValid) onRename(name)
	}

	FormDialog(
		visible = true,
		marker = "§ RENAME",
		meta = Res.string.create_group_meta.get().uppercase(),
		title = Res.string.rename_group_title.get(),
		confirmLabel = Res.string.rename_group_button.get(),
		cancelLabel = Res.string.rename_group_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = onDismiss,
		onDismiss = onDismiss,
		confirmEnabled = validation.isValid,
	) {
		FormField(
			value = name,
			onValueChange = { name = it },
			label = Res.string.rename_group_heading.get(),
			autoFocus = true,
			error = validation.fieldError(name),
			onImeAction = ::submit,
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
	val error = descriptionError(description)

	fun submit() {
		if (error == null) onSave(description.ifBlank { null })
	}

	FormDialog(
		visible = true,
		marker = "§ ${Res.string.group_description_marker.get().uppercase()}",
		meta = groupDef.name.uppercase(),
		title = Res.string.group_description_title.get(),
		confirmLabel = Res.string.group_description_save_button.get(),
		cancelLabel = Res.string.group_description_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = onDismiss,
		onDismiss = onDismiss,
		confirmEnabled = error == null,
	) {
		FormField(
			value = description,
			onValueChange = { description = it },
			label = Res.string.group_description_heading.get(),
			autoFocus = true,
			singleLine = false,
			imeAction = ImeAction.Default,
			helper = Res.string.group_description_helper.get(),
			error = error,
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
	ConfirmationDialog(
		visible = true,
		title = Res.string.delete_group_title.get(),
		message = Res.string.delete_group_message.get(groupDef.name),
		confirmLabel = Res.string.delete_group_confirm.get(),
		cancelLabel = Res.string.delete_group_cancel.get(),
		onConfirm = onConfirm,
		onDismiss = onDismiss,
	)
}

@Composable
private fun descriptionError(description: String): String? =
	if (description.length > MAX_GROUP_DESCRIPTION_LENGTH) {
		Res.string.group_description_too_long.get(MAX_GROUP_DESCRIPTION_LENGTH)
	} else {
		null
	}
