package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectIndexRow

@Preview
@Composable
fun ProjectCardPreview() {
	val data = fakeProjectData()
	Column {
		Spacer(modifier = Modifier.size(32.dp))

		AppTheme(globalSettingsPreview, false) {
			Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
				ProjectIndexRow(true, 0, data, {}, {}, {})
			}
		}

		Spacer(modifier = Modifier.size(32.dp))

		AppTheme(globalSettingsPreview, true) {
			Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
				ProjectIndexRow(true, 0, data, {}, {}, {})
			}
		}
	}
}

@Preview
@Composable
fun ProjectCardCompactPreview() {
	val data = fakeProjectData()
	Column(modifier = Modifier.width(360.dp)) {
		Spacer(modifier = Modifier.size(32.dp))

		AppTheme(globalSettingsPreview, true) {
			Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
				ProjectIndexRow(false, 0, data, {}, {}, {})
				ProjectIndexRow(false, 1, data, {}, {}, {})
			}
		}
	}
}

fun fakeProjectData() = ProjectData(
	fakeProjectDef(),
	fakeProjectMetadata()
)