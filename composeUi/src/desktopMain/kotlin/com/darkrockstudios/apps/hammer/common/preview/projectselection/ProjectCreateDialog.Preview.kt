package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projectselection.ImportHelpContent
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCreateMastheadActions
import com.darkrockstudios.apps.hammer.create_project_button
import com.darkrockstudios.apps.hammer.create_project_cancel_button
import com.darkrockstudios.apps.hammer.create_project_heading
import com.darkrockstudios.apps.hammer.create_project_marker
import com.darkrockstudios.apps.hammer.create_project_meta
import com.darkrockstudios.apps.hammer.create_project_title

/**
 * The create-project dialog renders inside an animated [androidx.compose.ui.window.Dialog], which
 * the Desktop preview renderer can't settle to a static frame. Preview its [FormDialogScaffold]
 * chrome directly so the masthead "Import" action and footer render opaque and deterministic.
 */
@Preview(widthDp = 720, heightDp = 460)
@Composable
fun ScreenProjectCreateDialogPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				FormDialogScaffold(
					marker = "§ ${Res.string.create_project_marker.get().uppercase()}",
					meta = Res.string.create_project_meta.get().uppercase(),
					title = Res.string.create_project_title.get(),
					confirmLabel = Res.string.create_project_button.get(),
					cancelLabel = Res.string.create_project_cancel_button.get(),
					onConfirm = {},
					onCancel = {},
					confirmEnabled = true,
					mastheadAction = { ProjectCreateMastheadActions(onHelp = {}, onImport = {}) },
					body = {
						FormField(
							value = "Alice in Wonderland",
							onValueChange = {},
							label = Res.string.create_project_heading.get(),
						)
					},
				)
			}
		}
	}
}

@Preview(widthDp = 600, heightDp = 460)
@Composable
fun ImportHelpDialogPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				ImportHelpContent(onDismiss = {})
			}
		}
	}
}
