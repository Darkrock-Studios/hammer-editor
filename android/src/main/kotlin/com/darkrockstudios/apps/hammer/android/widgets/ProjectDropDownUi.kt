package com.darkrockstudios.apps.hammer.android.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.serializableStateSaver
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

// TODO: extract HdDropdown when a second screen needs it
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDropDownUi(projects: List<ProjectDef>, onProjectSelected: (ProjectDef) -> Unit) {
	var expanded by remember { mutableStateOf(false) }
	var selected by rememberSaveable(
		saver = serializableStateSaver(ProjectDef.serializer())
	) {
		mutableStateOf(projects.first())
	}

	ExposedDropdownMenuBox(
		expanded = expanded,
		onExpandedChange = { expanded = !expanded },
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(
			modifier = Modifier
				.menuAnchor()
				.fillMaxWidth()
		) {
			HdMonoLabel(text = stringResource(R.string.note_widget_dialog_projects_dropdown))
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 6.dp, bottom = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = selected.name,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier.weight(1f),
				)
				Icon(
					imageVector = Icons.Default.ArrowDropDown,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}
		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			projects.forEach { project ->
				DropdownMenuItem(
					text = {
						Text(
							text = project.name,
							style = MaterialTheme.typography.bodyLarge,
							color = MaterialTheme.colorScheme.onSurface,
						)
					},
					onClick = {
						selected = project
						onProjectSelected(project)
						expanded = false
					},
				)
			}
		}
	}
}
