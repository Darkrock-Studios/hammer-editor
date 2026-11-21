package com.darkrockstudios.apps.hammer.common.preview.projectselection

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.rename_project_button
import com.darkrockstudios.apps.hammer.rename_project_cancel_button
import com.darkrockstudios.apps.hammer.rename_project_heading
import com.darkrockstudios.apps.hammer.rename_project_title

/** The rename-project form over a scrim; [FormDialogScaffold] directly, since the animated dialog can't settle in a preview. */
@Preview(widthDp = 600, heightDp = 400)
@Composable
fun ProjectRenameDialogPreview() {
	PreviewScrim {
		FormDialogScaffold(
			title = Res.string.rename_project_title.get(),
			subject = "Alice in Wonderland",
			confirmLabel = Res.string.rename_project_button.get(),
			cancelLabel = Res.string.rename_project_cancel_button.get(),
			onConfirm = {},
			onCancel = {},
			body = {
				FormField(
					value = "Alice in Wonderland",
					onValueChange = {},
					label = Res.string.rename_project_heading.get(),
				)
			},
		)
	}
}
