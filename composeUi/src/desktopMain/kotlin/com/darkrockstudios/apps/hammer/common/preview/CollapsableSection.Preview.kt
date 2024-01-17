package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.compose.CollapsableSection
import com.darkrockstudios.apps.hammer.common.compose.SpacerL

@Preview
@Composable
private fun CollapsableSectionPreview() {
	Column {
		CollapsableSection(
			false,
			Modifier.fillMaxWidth(),
			header = { Text("Header") }
		) {
			Column {
				Text("Body 1")
				Text("Body 2")
			}
		}

		SpacerL()

		CollapsableSection(
			true,
			Modifier.fillMaxWidth(),
			header = { Text("Header") }
		) {
			Column {
				Text("Body 1")
				Text("Body 2")
			}
		}
	}
}