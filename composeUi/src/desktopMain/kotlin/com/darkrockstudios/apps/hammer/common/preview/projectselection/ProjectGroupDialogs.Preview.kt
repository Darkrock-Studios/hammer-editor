package com.darkrockstudios.apps.hammer.common.preview.projectselection

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.projectselection.GroupCreateContent
import com.darkrockstudios.apps.hammer.common.projectselection.GroupDeleteContent
import com.darkrockstudios.apps.hammer.common.projectselection.GroupDescriptionContent
import com.darkrockstudios.apps.hammer.common.projectselection.GroupRenameContent

@Composable
private fun Scrim(content: @Composable () -> Unit) {
	PreviewScrim {
		content()
	}
}

@Preview(widthDp = 600, heightDp = 500)
@Composable
fun GroupCreateDialogPreview() = Scrim {
	GroupCreateContent(
		parentName = "Fantasy",
		name = "",
		onNameChange = {},
		nameError = null,
		nameValid = false,
		description = "",
		onDescriptionChange = {},
		onCreate = { _, _ -> },
		onCancel = {},
	)
}

@Preview(widthDp = 600, heightDp = 400)
@Composable
fun GroupRenameDialogPreview() = Scrim {
	GroupRenameContent(
		currentName = "Trilogy",
		name = "Trilogy",
		onNameChange = {},
		nameError = null,
		nameValid = true,
		onRename = {},
		onCancel = {},
	)
}

@Preview(widthDp = 600, heightDp = 500)
@Composable
fun GroupDescriptionDialogPreview() = Scrim {
	GroupDescriptionContent(
		groupName = "Trilogy",
		description = "The three books set in the northern kingdoms.",
		onDescriptionChange = {},
		onSave = {},
		onCancel = {},
	)
}

@Preview(widthDp = 600, heightDp = 400)
@Composable
fun GroupDeleteDialogPreview() = Scrim {
	GroupDeleteContent(groupName = "Trilogy", onConfirm = {}, onCancel = {})
}
