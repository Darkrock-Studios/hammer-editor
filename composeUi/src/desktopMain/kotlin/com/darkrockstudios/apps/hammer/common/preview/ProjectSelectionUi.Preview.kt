package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectIndexRow

@Preview
@Composable
private fun ProjectCardPreview() {
	val data = fakeProjectData()
	Column {
		Spacer(modifier = Modifier.size(32.dp))

		AppTheme(globalSettingsPreview, false) {
			ProjectIndexRow(true, 0, data, {}, {}, {})
		}

		Spacer(modifier = Modifier.size(32.dp))

		AppTheme(globalSettingsPreview, true) {
			ProjectIndexRow(true, 0, data, {}, {}, {})
		}
	}
}

fun fakeProjectData() = ProjectData(
	fakeProjectDef(),
	fakeProjectMetadata()
)