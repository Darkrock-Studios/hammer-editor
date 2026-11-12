package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.delete_project_cancel
import com.darkrockstudios.apps.hammer.leave_project_confirm
import com.darkrockstudios.apps.hammer.leave_project_copy_kept
import com.darkrockstudios.apps.hammer.leave_project_owner_untouched
import com.darkrockstudios.apps.hammer.leave_project_owner_untouched_unnamed
import com.darkrockstudios.apps.hammer.leave_project_title

private val DialogMaxWidth = 480.dp

/** In place of the delete dialog for a project shared with this account. */
@Composable
fun ProjectLeaveDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
	ownerName: String?,
	close: () -> Unit,
) {
	var isOpen by remember { mutableStateOf(true) }
	val ownerUntouched = ownerName?.takeIf { it.isNotBlank() }
		?.let { Res.string.leave_project_owner_untouched.get(it) }
		?: Res.string.leave_project_owner_untouched_unnamed.get()

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
			border = BorderStroke(width = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant),
		) {
			Column {
				HdMasthead(
					section = "LEAVE PROJECT",
					leadingMeta = listOf(projectDef.name),
					trailing = { HdMastheadAction(label = "× CLOSE", onClick = { isOpen = false }) },
				)
				HdFolioDivider()

				Text(
					text = Res.string.leave_project_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					modifier = Modifier
						.fillMaxWidth()
						.padding(start = Ui.Padding.XL, end = Ui.Padding.XL, top = Ui.Padding.L, bottom = Ui.Padding.S),
				)

				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				) {
					listOf(ownerUntouched, Res.string.leave_project_copy_kept.get()).forEach { paragraph ->
						Text(
							text = paragraph,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}

				HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				) {
					HdHairlineButton(label = Res.string.delete_project_cancel.get(), onClick = { isOpen = false })
					Spacer(modifier = Modifier.weight(1f))
					HdHairlineButton(
						label = Res.string.leave_project_confirm.get(),
						onClick = {
							component.leaveProject(projectDef)
							isOpen = false
						},
						emphasised = true,
					)
				}
			}
		}
	}
}
