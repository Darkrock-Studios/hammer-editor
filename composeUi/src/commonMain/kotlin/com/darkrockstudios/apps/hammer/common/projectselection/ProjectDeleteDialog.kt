package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

private val DialogMaxWidth = 480.dp

@Composable
fun ProjectDeleteDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
	close: () -> Unit
) {
	var isOpen by remember { mutableStateOf(true) }
	var confirmationText by rememberSaveable { mutableStateOf("") }
	val isConfirmed = confirmationText.trim().equals(projectDef.name, ignoreCase = true)
	val showMismatch = confirmationText.isNotBlank() && !isConfirmed

	AnimatedDialogContainer(
		isOpen = isOpen,
		onDismissRequest = { isOpen = false },
		onClosed = close,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Surface(
			modifier = Modifier
				.padding(Ui.Padding.M)
				.widthIn(max = DialogMaxWidth)
				.fillMaxWidth()
				.predictiveBackTransform(),
			shape = RectangleShape,
			color = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface,
			border = BorderStroke(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			),
		) {
			Column {
				Masthead(
					projectName = projectDef.name,
					onClose = { isOpen = false },
				)
				HdFolioDivider()

				Text(
					text = Res.string.delete_project_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier
						.fillMaxWidth()
						.padding(
							start = Ui.Padding.XL,
							end = Ui.Padding.XL,
							top = Ui.Padding.L,
							bottom = Ui.Padding.S,
						),
				)

				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(
							start = Ui.Padding.XL,
							end = Ui.Padding.XL,
							top = Ui.Padding.L,
							bottom = Ui.Padding.L,
						),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				) {
					Text(
						text = Res.string.delete_project_warning.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.error,
					)

					HdHairlineField(
						label = "CONFIRM NAME",
						value = confirmationText,
						onValueChange = { confirmationText = it },
						placeholder = Res.string.delete_project_confirm_hint.get(
							projectDef.name,
						),
						hint = projectDef.name,
						singleLine = true,
						imeAction = ImeAction.Done,
						error = if (showMismatch) "Does not match" else null,
					)
				}

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				ActionRow(
					confirmEnabled = isConfirmed,
					onCancel = { isOpen = false },
					onConfirm = {
						component.deleteProject(projectDef)
						isOpen = false
					},
				)
			}
		}
	}
}

@Composable
private fun Masthead(
	projectName: String,
	onClose: () -> Unit,
) {
	HdMasthead(
		section = "DELETE PROJECT",
		leadingMeta = listOf(projectName),
		trailing = { HdMastheadAction(label = "× CLOSE", onClick = onClose) },
	)
}

@Composable
private fun ActionRow(
	confirmEnabled: Boolean,
	onCancel: () -> Unit,
	onConfirm: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdHairlineButton(
			label = Res.string.delete_project_cancel.get(),
			onClick = onCancel,
		)
		Spacer(modifier = Modifier.weight(1f))
		HdHairlineButton(
			label = Res.string.delete_project_confirm.get(),
			onClick = onConfirm,
			danger = true,
			enabled = confirmEnabled,
		)
	}
}
