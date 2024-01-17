package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.animation.*
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@Composable
fun CollapsableSection(
	startExpanded: Boolean = false,
	modifier: Modifier = Modifier,
	header: @Composable () -> Unit,
	body: @Composable () -> Unit,
) {
	var expanded by rememberSaveable { mutableStateOf(startExpanded) }

	Column(modifier = modifier) {
		Row(
			modifier = Modifier.clickable { expanded = expanded.not() },
			verticalAlignment = Alignment.CenterVertically
		) {
			header()

			if (expanded) {
				Icon(Icons.Filled.ExpandLess, MR.strings.collapse.get())
			} else {
				Icon(Icons.Filled.ExpandMore, MR.strings.expand.get())
			}
		}
		AnimatedVisibility(
			visible = expanded,
			enter = slideInVertically() + fadeIn(),
			exit = slideOutVertically() + fadeOut()
		) {
			body()
		}
	}
}

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